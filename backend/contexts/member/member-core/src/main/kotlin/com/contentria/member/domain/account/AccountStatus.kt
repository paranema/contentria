package com.contentria.member.domain.account

/**
 * There is no unverified state. An account is created only after its email address has been
 * confirmed (a sign-up code for email, the provider's `email_verified` claim for social), so
 * every account begins ACTIVE.
 */
enum class AccountStatus {

    ACTIVE,

    /** Blocked by an administrator. Reversible. */
    SUSPENDED,

    /** Closed by the owner. Terminal. */
    DELETED;

    val canAuthenticate: Boolean get() = this == ACTIVE

    fun canTransitionTo(next: AccountStatus): Boolean = this != next && this != DELETED
}
