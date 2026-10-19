package com.contentria.member.application.service

import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.MemberUseCaseException.VerificationFailed.Reason
import com.contentria.member.domain.verification.CodeVerification
import com.contentria.member.domain.verification.EmailCodeAttempt
import org.springframework.transaction.support.TransactionTemplate

/** How a transaction that checked a code ended. */
internal sealed interface CodeChecked<out T> {
    class Done<T>(val value: T) : CodeChecked<T>
    class Rejected(val reason: Reason) : CodeChecked<Nothing>
}

/**
 * Runs [block] in a transaction and reports a rejection only after it has committed.
 *
 * A wrong code is recorded on the attempt inside [block]. Throwing there would roll the count back
 * with the transaction and make the five-attempt limit meaningless (02-implementation, 실패 횟수).
 */
internal fun <T> TransactionTemplate.runCodeChecked(block: () -> CodeChecked<T>): T =
    when (val outcome = execute { block() }!!) {
        is CodeChecked.Done -> outcome.value
        is CodeChecked.Rejected -> throw MemberUseCaseException.VerificationFailed(outcome.reason)
    }

/**
 * Keeps what [verify][EmailCodeAttempt.verify] decided: a failure count is saved, an expired
 * attempt is deleted. Null when the code verified; the caller then spends the attempt.
 */
internal fun <A : EmailCodeAttempt> CodeVerification.recordOn(attempt: A, save: (A) -> Unit, delete: (A) -> Unit): Reason? =
    when (this) {
        CodeVerification.VERIFIED -> null
        CodeVerification.MISMATCH -> { save(attempt); Reason.MISMATCH }
        CodeVerification.LOCKED -> { save(attempt); Reason.LOCKED }
        CodeVerification.EXPIRED -> { delete(attempt); Reason.EXPIRED }
    }
