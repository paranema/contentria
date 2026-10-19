package com.contentria.member.application.service

import com.contentria.member.api.AuthenticateAccessTokenQuery
import com.contentria.member.api.AuthenticatedAccount
import com.contentria.member.application.required.AccessTokenCodec
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class AccessTokenAuthenticationService(
    private val accessTokens: AccessTokenCodec,
    private val clock: Clock,
) : AuthenticateAccessTokenQuery {

    override fun authenticate(accessToken: String): AuthenticatedAccount? =
        accessTokens.verify(accessToken, clock.instant())
            ?.let { AuthenticatedAccount(it.accountId, it.roles.map { role -> role.name }.toSet()) }
}
