package com.contentria.member.application.service

import com.contentria.member.application.provided.CompleteWithdrawalUseCase
import com.contentria.member.application.provided.MemberUseCaseException.VerificationFailed.Reason
import com.contentria.member.application.provided.StartWithdrawalUseCase
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.application.required.CodePurpose
import com.contentria.member.application.required.EmailChangeAttemptRepository
import com.contentria.member.application.required.LoginAttemptRepository
import com.contentria.member.application.required.PasswordTokenRepository
import com.contentria.member.application.required.ProfileRepository
import com.contentria.member.application.required.VerificationCodeGenerator
import com.contentria.member.application.required.VerificationCodeSender
import com.contentria.member.application.required.WithdrawalAttemptRepository
import com.contentria.member.domain.withdrawal.WithdrawalAttempt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/**
 * docs/contexts/member/01-rules.md, 흐름 4의 탈퇴.
 *
 * ACTIVE only; a SUSPENDED account is refused and handled by contact. The account and its profile
 * change in one transaction, the second exception to "one aggregate per transaction" after
 * sign-up: a closed account with an unmarked profile would show a departed member as present,
 * and no other flow changes both at once.
 */
@Service
class WithdrawalService(
    private val accounts: AccountRepository,
    private val profiles: ProfileRepository,
    private val attempts: WithdrawalAttemptRepository,
    private val emailChanges: EmailChangeAttemptRepository,
    private val loginAttempts: LoginAttemptRepository,
    private val passwordTokens: PasswordTokenRepository,
    private val codes: VerificationCodeGenerator,
    private val codeSender: VerificationCodeSender,
    private val sessions: SessionService,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : StartWithdrawalUseCase, CompleteWithdrawalUseCase {

    @Transactional
    override fun start(accountId: UUID): UUID {
        val account = accounts.loadActive(accountId)
        attempts.deleteByAccountId(account.id)

        val code = codes.generate()
        val attempt = attempts.save(WithdrawalAttempt.start(account.id, account.email, code, clock.instant()))
        codeSender.send(account.email, code, CodePurpose.WITHDRAWAL)
        return attempt.id
    }

    override fun complete(accountId: UUID, attemptId: UUID, code: String) =
        transactions.runCodeChecked {
            val attempt = attempts.findById(attemptId)?.takeIf { it.accountId == accountId }
                ?: return@runCodeChecked CodeChecked.Rejected(Reason.EXPIRED)
            attempt.verify(code, clock.instant())
                .recordOn(attempt, attempts::save, attempts::delete)
                ?.let { return@runCodeChecked CodeChecked.Rejected(it) }

            val account = accounts.loadActive(accountId)
            val previousEmail = account.email
            account.close()
            profiles.findById(accountId)?.markWithdrawn(clock.instant())

            // Nothing that could reach the account again survives it.
            attempts.deleteByAccountId(accountId)
            emailChanges.deleteByAccountId(accountId)
            loginAttempts.deleteByEmail(previousEmail)
            passwordTokens.deleteByAccountId(accountId)
            sessions.endAllFor(accountId)
            CodeChecked.Done(Unit)
        }
}
