package com.contentria.member.domain.account

import com.contentria.member.domain.MemberSchema
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

/**
 * Who someone is, and how they prove it.
 *
 * Root of the Account aggregate. It owns the email address, the account's lifecycle status,
 * the authorities granted to it, and its [Credential]s. Anything a reader would put on a page
 * (a display name, a picture) belongs to the Profile aggregate instead, so that rendering a
 * post's author never has to load an email address or a role set.
 *
 * Every other aggregate refers to an account by its [id] only. There is no JPA association
 * pointing into or out of this aggregate.
 *
 * The rules this class enforces are listed in docs/contexts/member/01-rules.md, the rows whose
 * layer is `도메인`.
 */
@Entity
@Table(name = "accounts", schema = MemberSchema.NAME)
class Account private constructor(
    id: UUID,
    email: String,
    status: AccountStatus,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    /**
     * The account's contact address and its login identifier. Always an address someone has
     * proved they control: nothing assigns it without a confirmation step having happened first.
     *
     * Held here and nowhere else. Because [Credential] carries no copy, changing it is a single
     * assignment with no second table to keep in step.
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
     * a couple of rows.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "account_roles",
        schema = MemberSchema.NAME,
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
     * here, only that the identity is still linked to this account and that the account is in
     * a state that permits a session. The provider's `email_verified` claim is not looked at:
     * an existing credential means it was true when the identity was first linked.
     */
    fun authenticateWithSocialProvider(provider: AuthProvider, providerId: String) {
        if (!status.canAuthenticate) throw AccountException.NotAuthenticatable(status)

        val credential = credentialFor(provider)
            ?: throw AccountException.ProviderNotLinked(provider)

        if (credential.providerId != providerId) throw AccountException.InvalidCredentials()
    }

    // ---------------------------------------------------------------- credentials

    /**
     * Manual linking from the settings screen. The caller is signed in and has just passed the
     * provider's own authentication, so both sides are proven and `email_verified` is not
     * required. The account's email address does not change.
     */
    fun linkSocialProvider(provider: AuthProvider, providerId: String) {
        require(!provider.usesPassword) { "$provider is not a social provider" }
        if (credentialFor(provider) != null) throw AccountException.ProviderAlreadyLinked(provider)
        credentialList.add(Credential.withSocialProvider(this, provider, providerId))
    }

    /**
     * Automatic linking during a social sign-in whose address matches this account.
     *
     * Unlike [linkSocialProvider], nobody is signed in: the only thing tying the social identity
     * to this account is the address. So the provider must have verified it, it must be this
     * account's address, and the account must be active. A suspended or closed account is never
     * reached through a new identity.
     */
    fun autoLinkSocialProvider(
        provider: AuthProvider,
        providerId: String,
        providerEmail: String,
        emailVerifiedByProvider: Boolean,
    ) {
        if (!emailVerifiedByProvider) throw AccountException.EmailNotVerifiedByProvider(provider)
        if (!status.canAuthenticate) throw AccountException.NotAuthenticatable(status)
        require(normalizeEmail(providerEmail) == email) {
            "Automatic linking needs the provider's address to be this account's address"
        }
        linkSocialProvider(provider, providerId)
    }

    /** Orphan removal deletes the row; the check keeps the account from losing every way in. */
    fun unlinkProvider(provider: AuthProvider) {
        val credential = credentialFor(provider)
            ?: throw AccountException.ProviderNotLinked(provider)

        if (credentialList.size == 1) throw AccountException.LastCredentialRemoval()
        credentialList.remove(credential)
    }

    /**
     * Sets the password, creating the email credential if this account has none yet.
     *
     * Takes a hash: the raw value exists only in the use case that received it. Covers the
     * first password on a social-only account, a reset, and a change. Checking the current
     * password before a change is the use case's job, through [authenticateWithPassword].
     */
    fun setPassword(passwordHash: String) {
        val credential = credentialFor(AuthProvider.EMAIL)
        if (credential == null) {
            credentialList.add(Credential.withPassword(this, passwordHash))
        } else {
            credential.replacePasswordHash(passwordHash)
        }
    }

    fun hasCredentialFor(provider: AuthProvider): Boolean = credentialFor(provider) != null

    private fun credentialFor(provider: AuthProvider): Credential? =
        credentialList.firstOrNull { it.provider == provider }

    // ---------------------------------------------------------------- lifecycle

    fun suspend() = transitionTo(AccountStatus.SUSPENDED)

    fun reinstate() = transitionTo(AccountStatus.ACTIVE)

    /**
     * Closes the account and lets go of everything that identified its owner.
     *
     * Changing the status alone would keep the address and the social identities occupied,
     * and the same person could never sign up again. The row stays, so other aggregates that
     * hold this [id] still resolve.
     */
    fun close() {
        transitionTo(AccountStatus.DELETED)
        credentialList.clear()
        email = "deleted+$id@$ANONYMISED_EMAIL_DOMAIN"
    }

    /**
     * Replaces the address with one the owner has already confirmed.
     *
     * The confirmation itself (a code sent to the new address, a notice to the old one) belongs
     * to the use case. The account's address decides automatic linking, so assigning an
     * unconfirmed one would let anyone claim another person's social sign-in.
     */
    fun changeToVerifiedEmail(verifiedEmail: String) {
        email = normalizeEmail(verifiedEmail)
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

        /** `.invalid` is reserved (RFC 2606) and can never be a real mailbox. */
        private const val ANONYMISED_EMAIL_DOMAIN = "deleted.invalid"

        /**
         * Deliberately loose. Anything stricter rejects addresses that are valid in practice,
         * and the only proof that an address works is a verification mail reaching it.
         */
        private val EMAIL_PATTERN = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

        /**
         * Email sign-up, called after the sign-up code has been confirmed. Active immediately,
         * because the address is already proven. Takes a hash: the raw password never enters
         * the aggregate.
         */
        fun registerWithPassword(email: String, passwordHash: String): Account {
            val account = Account(
                id = Ids.newId(),
                email = normalizeEmail(email),
                status = AccountStatus.ACTIVE,
            )
            account.roleSet.add(Role.USER)
            account.credentialList.add(Credential.withPassword(account, passwordHash))
            return account
        }

        /**
         * Social sign-up. Active immediately, but only if the provider verified the address:
         * the address is what later decides automatic linking, so an unverified one could hand
         * this account to whoever really owns it.
         */
        fun registerWithSocialProvider(
            email: String,
            provider: AuthProvider,
            providerId: String,
            emailVerifiedByProvider: Boolean,
        ): Account {
            if (!emailVerifiedByProvider) throw AccountException.EmailNotVerifiedByProvider(provider)

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
         * registered. Dots and `+` suffixes are kept. The unique constraint on `accounts.email`
         * only helps if the stored form is canonical.
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
