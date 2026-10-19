package com.contentria.member.api

import java.util.UUID

/**
 * Turns an access token into the account it was issued to. Used by the request-authentication
 * filter of every app that serves member endpoints.
 *
 * Returns null for anything that is not a valid, unexpired token issued by this context.
 */
interface AuthenticateAccessTokenQuery {
    fun authenticate(accessToken: String): AuthenticatedAccount?
}

data class AuthenticatedAccount(val accountId: UUID, val roles: Set<String>)
