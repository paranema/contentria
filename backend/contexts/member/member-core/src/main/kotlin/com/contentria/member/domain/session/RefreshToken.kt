package com.contentria.member.domain.session

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
 * One live refresh token (docs/conventions/security.md, Refresh Token Rotation).
 *
 * Its own aggregate: it refers to the account by id only, and is created and destroyed on a
 * different rhythm from the account. Only a hash of the token is stored. The raw value is a
 * bearer credential and exists only in the response that hands it out.
 */
@Entity
@Table(name = "refresh_tokens", schema = MemberSchema.NAME)
class RefreshToken private constructor(
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
        this === other || (other is RefreshToken && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    /** The stored token and the raw value to hand to the client. */
    class Issued(val token: RefreshToken, val rawValue: String)

    companion object {

        private val random = SecureRandom()

        fun issue(accountId: UUID, now: Instant, ttl: Duration): Issued {
            val bytes = ByteArray(32).also(random::nextBytes)
            val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val token = RefreshToken(Ids.newId(), accountId, hashOf(raw), now.plus(ttl))
            return Issued(token, raw)
        }

        fun hashOf(rawValue: String): String = Sha256.hex(rawValue)
    }
}
