package com.contentria.member.application.service

import com.contentria.member.application.provided.CompleteEmailChangeUseCase
import com.contentria.member.application.provided.IssuedSession
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.MemberUseCaseException.VerificationFailed.Reason
import com.contentria.member.application.provided.StartEmailChangeUseCase
import com.contentria.member.application.required.AccountNotice
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.application.required.CodePurpose
import com.contentria.member.application.required.EmailChangeAttemptRepository
import com.contentria.member.application.required.PasswordTokenRepository
import com.contentria.member.application.required.VerificationCodeGenerator
import com.contentria.member.application.required.VerificationCodeSender
import com.contentria.member.domain.account.EmailAddresses
import com.contentria.member.domain.emailchange.EmailChangeAttempt
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/**
 * docs/contexts/member/01-rules.md, 흐름 4의 이메일 변경: 새 주소 확인 + 기존 주소 알림, 확인 전까지 미반영.
 *
 * The current address also gets a code that must come back. A stolen session alone would
 * otherwise move the account to an address the thief controls, and a reset link to that address
 * would finish the takeover.
 */
@Service
class EmailChangeService(
    private val accounts: AccountRepository,
    private val attempts: EmailChangeAttemptRepository,
    private val passwordTokens: PasswordTokenRepository,
    private val codes: VerificationCodeGenerator,
    private val codeSender: VerificationCodeSender,
    private val sessions: SessionService,
    private val events: ApplicationEventPublisher,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : StartEmailChangeUseCase, CompleteEmailChangeUseCase {

    @Transactional
    override fun start(accountId: UUID, newEmail: String): UUID {
        val account = accounts.loadActive(accountId)
        val normalized = EmailAddresses.normalize(newEmail)
        if (normalized == account.email) throw MemberUseCaseException.EmailUnchanged()
        if (accounts.findByEmail(normalized) != null) throw MemberUseCaseException.EmailInUse()

        // A resend invalidates the earlier pair of codes.
        attempts.deleteByAccountId(account.id)

        val currentAddressCode = codes.generate()
        val newAddressCode = codes.generate()
        val attempt = attempts.save(EmailChangeAttempt.start(account.id, normalized, currentAddressCode, newAddressCode, clock.instant()))
        codeSender.send(account.email, currentAddressCode, CodePurpose.EMAIL_CHANGE_CURRENT)
        codeSender.send(normalized, newAddressCode, CodePurpose.EMAIL_CHANGE_NEW)
        return attempt.id
    }

    override fun complete(accountId: UUID, attemptId: UUID, currentAddressCode: String, newAddressCode: String): IssuedSession =
        transactions.runCodeChecked {
            // Only the account that asked can finish; anyone else's id reads as unknown.
            val attempt = attempts.findById(attemptId)?.takeIf { it.accountId == accountId }
                ?: return@runCodeChecked CodeChecked.Rejected(Reason.EXPIRED)
            attempt.verify(currentAddressCode, newAddressCode, clock.instant())
                .recordOn(attempt, attempts::save, attempts::delete)
                ?.let { return@runCodeChecked CodeChecked.Rejected(it) }
            attempts.delete(attempt)

            val account = accounts.loadActive(accountId)
            // Taken since the codes went out. The unique constraint is the backstop for a race.
            if (accounts.findByEmail(attempt.email) != null) throw MemberUseCaseException.EmailInUse()

            val previous = account.email
            account.changeToVerifiedEmail(attempt.email)
            // Links already mailed to the old address must not outlive the move.
            passwordTokens.deleteByAccountId(account.id)
            events.publishEvent(AccountNoticeRequested(previous, AccountNotice.EmailChanged))
            CodeChecked.Done(sessions.restartFor(account))
        }
}
