package com.contentria.member.domain.account

import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.util.UUID
import kotlin.uuid.Uuid

/**
 * Who someone is, and how they prove it.
 *
 * Root of the Account aggregate. It owns the email address, the account's lifecycle status,
 * the authorities granted to it, and its [Credential]s. Anything a reader would put on a page
 * — a display name, a picture — belongs to the Profile context instead, so that rendering a
 * post's author never has to load an email address or a role set.
 *
 * Every other context refers to an account by its [id] only. There is no JPA association
 * pointing into or out of this aggregate.
 */
@Entity
@Table(name = "accounts")
class Account private constructor(
    id: UUID,
    email: String,
    status: AccountStatus,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    /**
     * The account's contact address and its login identifier.
     *
     * Held here and nowhere else. Because [Credential] no longer carries a copy, changing it
     * is a single assignment with no second table to keep in step.
     */
    @Column(name = "email", nullable = false, unique = true, length = EMAIL_MAX_LENGTH)
    var email: String = email
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: AccountStatus = status
        protected set

    @OneToMany(
        mappedBy = "account",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    private val credentialList: MutableList<Credential> = mutableListOf()

    /** A copy, so callers cannot add or remove credentials behind the invariants below. */
    val credentials: List<Credential>
        get() = credentialList.toList()

    /**
     * Eager because every authenticated request needs the authorities, and the set is at most
     * a couple of rows. This replaces the previous `roles` table, `user_roles` join table and
     * the two entities that mapped them.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "account_roles",
        joinColumns = [JoinColumn(name = "account_id")],
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private val roleSet: MutableSet<Role> = mutableSetOf()

    val roles: Set<Role>
        get() = roleSet.toSet()

    // ---------------------------------------------------------------- authentication

    /**
     * Verifies a raw password against this account's email credential.
     *
     * Returns nothing on success. The credential is not handed back, so neither the hash nor
     * the member entity escapes the aggregate.
     */
    fun authenticateWithPassword(rawPassword: String, hasher: PasswordHasher) {
        if (!status.canAuthenticate) throw AccountException.NotAuthenticatable(status)

        val credential = credentialFor(AuthProvider.EMAIL)
            ?: throw AccountException.InvalidCredentials()

        if (!credential.matchesPassword(rawPassword, hasher)) {
            throw AccountException.InvalidCredentials()
        }
    }

    /**
     * Confirms this account may start a session through a social provider.
     *
     * The provider has already established who the caller is, so there is no secret to check
     * here — only that the identity is still linked to this account and that the account is
     * in a state that permits a session.
     */
    fun authenticateWithSocialProvider(provider: AuthProvider, providerId: String) {
        if (!status.canAuthenticate) throw AccountException.NotAuthenticatable(status)

        val credential = credentialFor(provider)
            ?: throw AccountException.ProviderNotLinked(provider)

        if (credential.providerId != providerId) throw AccountException.InvalidCredentials()
    }

    // ---------------------------------------------------------------- credentials

    fun linkSocialProvider(provider: AuthProvider, providerId: String) {
        require(!provider.usesPassword) { "$provider is not a social provider" }
        if (credentialFor(provider) != null) throw AccountException.ProviderAlreadyLinked(provider)
        credentialList.add(Credential.withSocialProvider(this, provider, providerId))
    }

    /** Orphan removal deletes the row; the check keeps the account from losing every way in. */
    fun unlinkProvider(provider: AuthProvider) {
        val credential = credentialFor(provider)
            ?: throw AccountException.ProviderNotLinked(provider)

        if (credentialList.size == 1) throw AccountException.LastCredentialRemoval()
        credentialList.remove(credential)
    }

    fun changePassword(rawPassword: String, hasher: PasswordHasher) {
        val credential = credentialFor(AuthProvider.EMAIL)
            ?: throw AccountException.ProviderNotLinked(AuthProvider.EMAIL)

        credential.replacePasswordHash(hasher.hash(rawPassword))
    }

    fun hasCredentialFor(provider: AuthProvider): Boolean = credentialFor(provider) != null

    private fun credentialFor(provider: AuthProvider): Credential? =
        credentialList.firstOrNull { it.provider == provider }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Idempotent for an account that is already active: confirming a verified address twice
     * is not an error. Every other status is rejected — verifying an email address is a
     * sign-up step and must never be a way to lift a suspension.
     */
    fun verifyEmail() {
        if (status == AccountStatus.ACTIVE) return
        if (status != AccountStatus.UNVERIFIED) {
            throw AccountException.StatusTransitionNotAllowed(status, AccountStatus.ACTIVE)
        }
        status = AccountStatus.ACTIVE
    }

    fun suspend() = transitionTo(AccountStatus.SUSPENDED)

    fun reinstate() = transitionTo(AccountStatus.ACTIVE)

    fun close() = transitionTo(AccountStatus.DELETED)

    fun changeEmail(newEmail: String) {
        email = normalizeEmail(newEmail)
    }

    private fun transitionTo(next: AccountStatus) {
        if (!status.canTransitionTo(next)) {
            throw AccountException.StatusTransitionNotAllowed(status, next)
        }
        status = next
    }

    // ---------------------------------------------------------------- authorities

    fun grant(role: Role) {
        roleSet.add(role)
    }

    fun revoke(role: Role) {
        if (roleSet.size == 1 && role in roleSet) throw AccountException.LastRoleRemoval()
        roleSet.remove(role)
    }

    fun hasRole(role: Role): Boolean = role in roleSet

    override fun equals(other: Any?): Boolean =
        this === other || (other is Account && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {

        const val EMAIL_MAX_LENGTH = 255

        /**
         * Deliberately loose. Anything stricter rejects addresses that are valid in practice,
         * and the only proof that an address works is a verification mail reaching it.
         */
        private val EMAIL_PATTERN = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

        /**
         * Email sign-up. The account cannot start a session until [verifyEmail] is called,
         * because at this point nothing shows the address belongs to the registrant.
         */
        fun registerWithPassword(
            email: String,
            rawPassword: String,
            hasher: PasswordHasher,
        ): Account {
            val account = Account(
                id = Ids.newId(),
                email = normalizeEmail(email),
                status = AccountStatus.UNVERIFIED,
            )
            account.roleSet.add(Role.USER)
            account.credentialList.add(Credential.withPassword(account, hasher.hash(rawPassword)))
            return account
        }

        /**
         * Social sign-up. Active immediately: the provider has already verified the address,
         * so asking the registrant to confirm it a second time proves nothing.
         */
        fun registerWithSocialProvider(
            email: String,
            provider: AuthProvider,
            providerId: String,
        ): Account {
            val account = Account(
                id = Ids.newId(),
                email = normalizeEmail(email),
                status = AccountStatus.ACTIVE,
            )
            account.roleSet.add(Role.USER)
            account.credentialList.add(
                Credential.withSocialProvider(account, provider, providerId),
            )
            return account
        }

        /**
         * Lower-cases and trims before validating, so `A@x.com` and `a@x.com` cannot both be
         * registered. The unique constraint on `accounts.email` only helps if the stored form
         * is canonical.
         */
        private fun normalizeEmail(raw: String): String {
            val normalized = raw.trim().lowercase()
            if (normalized.length > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matches(normalized)) {
                throw AccountException.InvalidEmail(raw)
            }
            return normalized
        }
    }
}
