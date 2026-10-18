package com.contentria.member.domain.account

import com.contentria.shared.exception.DomainException

sealed class AccountException(message: String) : DomainException(message) {

    class InvalidEmail(value: String) :
        AccountException("Not a usable email address: $value")

    /**
     * Deliberately says nothing about which half failed, or whether the account exists at
     * all, so the response cannot be used to enumerate registered addresses.
     */
    class InvalidCredentials :
        AccountException("Email or password does not match")

    class NotAuthenticatable(status: AccountStatus) :
        AccountException("An account in status $status cannot start a session")

    class StatusTransitionNotAllowed(from: AccountStatus, to: AccountStatus) :
        AccountException("Cannot move an account from $from to $to")

    class ProviderAlreadyLinked(provider: AuthProvider) :
        AccountException("This account already has a $provider credential")

    class ProviderNotLinked(provider: AuthProvider) :
        AccountException("This account has no $provider credential")

    class LastCredentialRemoval :
        AccountException("Removing the last credential would leave the account unreachable")

    class LastRoleRemoval :
        AccountException("An account must keep at least one role")
}
