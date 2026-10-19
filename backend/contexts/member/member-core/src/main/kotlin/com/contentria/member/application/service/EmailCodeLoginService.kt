package com.contentria.member.application.service

import com.contentria.member.application.provided.CompleteEmailCodeLoginUseCase
import com.contentria.member.application.provided.IssuedSession
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.MemberUseCaseException.VerificationFailed.Reason
import com.contentria.member.application.provided.StartEmailCodeLoginUseCase
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.application.required.CodePurpose
import com.contentria.member.application.required.LoginAttemptRepository
import com.contentria.member.application.required.VerificationCodeGenerator
import com.contentria.member.application.required.VerificationCodeSender
import com.contentria.member.domain.account.EmailAddresses
import com.contentria.member.domain.login.LoginAttempt
import com.contentria.member.domain.verification.CodeVerification
import com.contentria.shared.id.Ids
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/** docs/contexts/member/01-rules.md, 흐름 6. 이메일 코드 로그인 */
@Service
class EmailCodeLoginService(
    private val accounts: AccountRepository,
    private val attempts: LoginAttemptRepository,
    private val codes: VerificationCodeGenerator,
    private val codeSender: VerificationCodeSender,
    private val sessions: SessionService,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : StartEmailCodeLoginUseCase, CompleteEmailCodeLoginUseCase {

    /**
     * 1번: 계정이 없으면 코드를 보내지 않는다. 2번: 시도를 만들고 코드를 보낸다.
     *
     * For an unknown address the id handed back is fresh and stored nowhere, so completing it
     * fails exactly the way an expired attempt does.
     */
    @Transactional
    override fun start(email: String): UUID {
        val normalized = EmailAddresses.normalize(email)
        if (accounts.findByEmail(normalized) == null) return Ids.newId()

        // A resend invalidates every earlier code for the address.
        attempts.deleteByEmail(normalized)

        val code = codes.generate()
        val attempt = attempts.save(LoginAttempt.start(normalized, code, clock.instant()))
        codeSender.send(normalized, code, CodePurpose.LOGIN)
        return attempt.id
    }

    /**
     * 3번: 코드가 맞고 계정이 ACTIVE면 세션을 시작한다.
     *
     * As in sign-up, a wrong code is recorded and committed before the failure is reported.
     */
    override fun complete(attemptId: UUID, code: String): IssuedSession {
        val outcome = transactions.execute { completeInTransaction(attemptId, code) }!!
        return when (outcome) {
            is Outcome.Completed -> outcome.session
            is Outcome.Rejected -> throw MemberUseCaseException.VerificationFailed(outcome.reason)
        }
    }

    private fun completeInTransaction(attemptId: UUID, code: String): Outcome {
        val attempt = attempts.findById(attemptId) ?: return Outcome.Rejected(Reason.EXPIRED)

        when (attempt.verify(code, clock.instant())) {
            CodeVerification.VERIFIED -> Unit
            CodeVerification.MISMATCH -> { attempts.save(attempt); return Outcome.Rejected(Reason.MISMATCH) }
            CodeVerification.LOCKED -> { attempts.save(attempt); return Outcome.Rejected(Reason.LOCKED) }
            CodeVerification.EXPIRED -> { attempts.delete(attempt); return Outcome.Rejected(Reason.EXPIRED) }
        }
        attempts.delete(attempt)

        // The address may have left its account since the code was sent: changed, or anonymised on closing.
        val account = accounts.findByEmail(attempt.email) ?: return Outcome.Rejected(Reason.EXPIRED)

        // Refusing an account that is not ACTIVE rolls the delete back with it. The attempt then
        // lives until it expires, and returning the code again is refused the same way.
        account.authenticateWithEmailCode(attempt.email)
        return Outcome.Completed(sessions.startFor(account))
    }

    private sealed interface Outcome {
        class Completed(val session: IssuedSession) : Outcome
        class Rejected(val reason: Reason) : Outcome
    }
}
