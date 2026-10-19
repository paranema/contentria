package com.contentria.member.domain.signup

import com.contentria.member.domain.MemberSchema
import com.contentria.member.domain.Sha256
import com.contentria.member.domain.account.EmailAddresses
import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * An email sign-up that has sent a code and is waiting for it to come back.
 *
 * Not part of the Account aggregate: there is no account yet, and nothing here is an account
 * invariant (docs/contexts/member/02-implementation.md, 애그리거트 경계). It holds only the
 * address and the code, never a password or any other input, so that two attempts for the same
 * address differ in nothing but the code (01-rules, 가입 시도에 사용자 입력을 담지 않는다).
 *
 * The attempt's [id] is what the requester gets back and must present with the code. That is
 * how "verifiable only by the session that asked for it" is enforced: nobody else holds the id.
 */
@Entity
@Table(name = "signup_attempts", schema = MemberSchema.NAME)
class SignUpAttempt private constructor(
    id: UUID,
    email: String,
    codeHash: String,
    expiresAt: Instant,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @Column(name = "email", nullable = false, updatable = false, length = EmailAddresses.MAX_LENGTH)
    val email: String = email

    /** Hash of the code, salted with [id]. The code itself is never stored. */
    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private val codeHash: String = codeHash

    @Column(name = "failed_attempts", nullable = false)
    var failedAttempts: Int = 0
        protected set

    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant = expiresAt

    /**
     * Returns rather than throws, so the caller can persist the failed-attempt count before it
     * reports the failure. A thrown exception would roll the count back with the transaction.
     */
    fun verify(code: String, now: Instant): CodeVerification = when {
        !now.isBefore(expiresAt) -> CodeVerification.EXPIRED
        failedAttempts >= MAX_FAILED_ATTEMPTS -> CodeVerification.LOCKED
        hash(id, code) != codeHash -> {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) CodeVerification.LOCKED else CodeVerification.MISMATCH
        }
        else -> CodeVerification.VERIFIED
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is SignUpAttempt && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {

        const val MAX_FAILED_ATTEMPTS = 5

        val VALIDITY: Duration = Duration.ofMinutes(10)

        fun start(email: String, code: String, now: Instant): SignUpAttempt {
            val id = Ids.newId()
            return SignUpAttempt(
                id = id,
                email = EmailAddresses.normalize(email),
                codeHash = hash(id, code),
                expiresAt = now.plus(VALIDITY),
            )
        }

        private fun hash(id: UUID, code: String): String = Sha256.hex("$id:$code")
    }
}
