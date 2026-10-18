package com.contentria.member.domain.account

import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/**
 * One way of proving ownership of an [Account]: a password, or a link to a social provider.
 *
 * A member of the Account aggregate, not a root. It has no identity outside the account it
 * belongs to, nothing else in the system references it, and it is only ever reached through
 * [Account]. Construction and mutation are therefore `internal` — [Account] is the only class
 * meant to call them.
 *
 * It deliberately holds no email address. The account owns the address; storing it here as
 * well is what left `users.email` and `credentials.email` as two copies of one value with no
 * code keeping them in step.
 */
@Entity
@Table(
    name = "credentials",
    uniqueConstraints = [
        // At most one credential per provider per account. This is the invariant
        // `addCredential` enforces in memory; the constraint is the backstop for two
        // concurrent transactions, which no in-memory check can catch.
        UniqueConstraint(
            name = "uq_credentials_account_provider",
            columnNames = ["account_id", "provider"],
        ),
        // A social identity belongs to exactly one account. Null provider_id rows (every
        // EMAIL credential) are distinct under this constraint in PostgreSQL, so they do
        // not collide.
        UniqueConstraint(
            name = "uq_credentials_provider_id",
            columnNames = ["provider", "provider_id"],
        ),
    ],
)
class Credential private constructor(
    id: UUID,
    account: Account,
    provider: AuthProvider,
    passwordHash: String?,
    providerId: String?,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    /**
     * Back-reference required by the `mappedBy` on [Account.credentials]. An association to
     * an object rather than a plain id column is allowed here precisely because both ends
     * sit inside the same aggregate; references that cross an aggregate boundary are stored
     * as a bare UUID instead.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    val account: Account = account

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    val provider: AuthProvider = provider

    /** Set only for [AuthProvider.EMAIL]. Private: the hash never leaves this class. */
    @Column(name = "password_hash", length = 255)
    private var passwordHash: String? = passwordHash

    /**
     * The subject identifier issued by the social provider, null for [AuthProvider.EMAIL].
     * This, not the email address, is the stable key for a social identity — a provider
     * account can change its address while remaining the same person.
     */
    @Column(name = "provider_id", length = 255)
    val providerId: String? = providerId

    internal fun matchesPassword(rawPassword: String, hasher: PasswordHasher): Boolean {
        val hash = passwordHash ?: return false
        return hasher.matches(rawPassword, hash)
    }

    internal fun replacePasswordHash(newHash: String) {
        check(provider.usesPassword) { "A $provider credential cannot hold a password" }
        require(newHash.isNotBlank()) { "passwordHash must not be blank" }
        passwordHash = newHash
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Credential && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {

        internal fun withPassword(account: Account, passwordHash: String): Credential {
            require(passwordHash.isNotBlank()) { "passwordHash must not be blank" }
            return Credential(
                id = Ids.newId(),
                account = account,
                provider = AuthProvider.EMAIL,
                passwordHash = passwordHash,
                providerId = null,
            )
        }

        internal fun withSocialProvider(
            account: Account,
            provider: AuthProvider,
            providerId: String,
        ): Credential {
            require(!provider.usesPassword) { "$provider is not a social provider" }
            require(providerId.isNotBlank()) { "providerId must not be blank" }
            return Credential(
                id = Ids.newId(),
                account = account,
                provider = provider,
                passwordHash = null,
                providerId = providerId,
            )
        }
    }
}
