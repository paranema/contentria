package com.contentria.member.domain.password

import com.contentria.member.domain.MemberSchema
import com.contentria.member.domain.Sha256
import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * The token in a password setup or reset link (docs/contexts/member/01-rules.md, 흐름 4: 설정 링크와
 * 재설정 링크의 토큰은 1회용이고, 만료가 있고, 계정에 묶인다).
 *
 * Its own aggregate, referring to the account by id. Only a hash is stored; the raw value exists
 * only in the mail. One use: the use case deletes the token when it is spent.
 */
@Entity
@Table(name = "password_tokens", schema = MemberSchema.NAME)
class PasswordToken private constructor(
    id: UUID,
    accountId: UUID,
    tokenHash: String,
    expiresAt: Instant,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @Column(name = "account_id", nullable = false, updatable = false)
    val accountId: UUID = accountId

    @Column(name = "token_hash", nullable = false, updatable = false, unique = true, length = 64)
    val tokenHash: String = tokenHash

    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant = expiresAt

    fun isUsableAt(now: Instant): Boolean = now.isBefore(expiresAt)

    override fun equals(other: Any?): Boolean =
        this === other || (other is PasswordToken && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    /** The stored token and the raw value to put in the link. */
    class Issued(val token: PasswordToken, val rawValue: String)

    companion object {

        val VALIDITY: Duration = Duration.ofMinutes(30)

        private val random = SecureRandom()

        fun issue(accountId: UUID, now: Instant): Issued {
            val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
            return Issued(PasswordToken(Ids.newId(), accountId, hashOf(raw), now.plus(VALIDITY)), raw)
        }

        fun hashOf(rawValue: String): String = Sha256.hex(rawValue)
    }
}
