package com.contentria.member.domain.account

enum class AccountStatus {

    /** Registered by email, waiting for the verification code to be confirmed. */
    UNVERIFIED,

    ACTIVE,

    /** Blocked by an administrator. Reversible. */
    SUSPENDED,

    /** Closed by the owner. Terminal. */
    DELETED;

    val canAuthenticate: Boolean get() = this == ACTIVE

    /**
     * DELETED is terminal, and no account returns to UNVERIFIED once it has left that state,
     * because verification is a one-time step at sign-up.
     */
    fun canTransitionTo(next: AccountStatus): Boolean = when {
        this == next -> false
        this == DELETED -> false
        next == UNVERIFIED -> false
        else -> true
    }
}
