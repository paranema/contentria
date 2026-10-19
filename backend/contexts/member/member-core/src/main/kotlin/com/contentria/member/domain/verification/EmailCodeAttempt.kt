package com.contentria.member.domain.verification

import com.contentria.member.domain.Sha256
import com.contentria.member.domain.account.EmailAddresses
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * A code sent to an address, waiting to come back.
 *
 * Sign-up and code login follow the same rules (docs/contexts/member/01-rules.md, 흐름 6):
 * ten minutes, five wrong codes, a resend kills earlier codes, only the requester can verify,
 * and only a hash is stored. Each purpose has its own table, so an attempt started for one can
 * never be completed as the other.
 *
 * The [id] is what the requester gets back and must present with the code. That is how
 * "verifiable only by the session that asked for it" is enforced: nobody else holds the id.
 */
@MappedSuperclass
abstract class EmailCodeAttempt protected constructor(
    id: UUID,
    email: String,
    code: String,
    now: Instant,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @Column(name = "email", nullable = false, updatable = false, length = EmailAddresses.MAX_LENGTH)
    val email: String = EmailAddresses.normalize(email)

    /** Hash of the code, salted with [id]. The code itself is never stored. */
    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private val codeHash: String = hash(id, code)

    @Column(name = "failed_attempts", nullable = false)
    var failedAttempts: Int = 0
        protected set

    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant = now.plus(VALIDITY)

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

    override fun hashCode(): Int = id.hashCode()

    companion object {

        const val MAX_FAILED_ATTEMPTS = 5

        val VALIDITY: Duration = Duration.ofMinutes(10)

        private fun hash(id: UUID, code: String): String = Sha256.hex("$id:$code")
    }
}
