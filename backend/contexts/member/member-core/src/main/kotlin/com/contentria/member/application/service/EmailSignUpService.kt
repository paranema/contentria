package com.contentria.member.application.service

import com.contentria.member.application.provided.CompleteEmailSignUpCommand
import com.contentria.member.application.provided.CompleteEmailSignUpUseCase
import com.contentria.member.application.provided.IssuedSession
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.MemberUseCaseException.VerificationFailed.Reason
import com.contentria.member.application.provided.StartEmailSignUpUseCase
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.application.required.CodePurpose
import com.contentria.member.application.required.SignUpAttemptRepository
import com.contentria.member.application.required.VerificationCodeGenerator
import com.contentria.member.application.required.VerificationCodeSender
import com.contentria.member.domain.account.Account
import com.contentria.member.domain.account.EmailAddresses
import com.contentria.member.domain.account.PasswordHasher
import com.contentria.member.domain.signup.SignUpAttempt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/** docs/contexts/member/01-rules.md, 흐름 1. 로컬 회원가입 */
@Service
class EmailSignUpService(
    private val accounts: AccountRepository,
    private val attempts: SignUpAttemptRepository,
    private val codes: VerificationCodeGenerator,
    private val codeSender: VerificationCodeSender,
    private val passwordHasher: PasswordHasher,
    private val profiles: ProfileService,
    private val sessions: SessionService,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : StartEmailSignUpUseCase, CompleteEmailSignUpUseCase {

    /** 1번: 같은 이메일 계정이 있으면 409. 2번: 시도를 만들고 코드를 보낸다. 계정은 만들지 않는다. */
    @Transactional
    override fun start(email: String): UUID {
        val normalized = EmailAddresses.normalize(email)
        rejectIfRegistered(normalized)

        // A resend invalidates every earlier code for the address.
        attempts.deleteByEmail(normalized)

        val code = codes.generate()
        val attempt = attempts.save(SignUpAttempt.start(normalized, code, clock.instant()))
        codeSender.send(normalized, code, CodePurpose.SIGN_UP)
        return attempt.id
    }

    /**
     * 3번: 코드가 맞으면 계정(ACTIVE), 로컬 자격증명, 프로필을 한 트랜잭션에서 만든다.
     *
     * A wrong code is recorded and committed before the failure is reported. Throwing inside
     * the transaction would roll the failed-attempt count back and make the limit meaningless.
     */
    override fun complete(command: CompleteEmailSignUpCommand): IssuedSession = transactions.runCodeChecked {
        val attempt = attempts.findById(command.attemptId) ?: return@runCodeChecked CodeChecked.Rejected(Reason.EXPIRED)
        attempt.verify(command.code, clock.instant())
            .recordOn(attempt, attempts::save, attempts::delete)
            ?.let { return@runCodeChecked CodeChecked.Rejected(it) }

        rejectIfRegistered(attempt.email)
        val account = accounts.save(Account.registerWithPassword(attempt.email, passwordHasher.hash(command.rawPassword)))
        profiles.createFor(account.id)
        attempts.delete(attempt)
        CodeChecked.Done(sessions.startFor(account))
    }

    private fun rejectIfRegistered(email: String) {
        val existing = accounts.findByEmail(email) ?: return
        throw MemberUseCaseException.EmailAlreadyRegistered(existing.credentials.map { it.provider.name }.toSet())
    }
}
