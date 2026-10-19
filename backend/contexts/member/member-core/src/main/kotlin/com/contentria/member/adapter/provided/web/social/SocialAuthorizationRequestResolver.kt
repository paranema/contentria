package com.contentria.member.adapter.provided.web.social

import com.contentria.member.api.AuthenticateAccessTokenQuery
import com.contentria.member.api.SessionCookieNames
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component

/**
 * Builds the redirect to the provider: Spring Security's default request (state, OIDC nonce)
 * plus PKCE, plus the intent.
 *
 * `/oauth2/authorization/{provider}` signs in; `?intent=link` links the provider to the account
 * signed in at that moment. That account is read here, from the access-token cookie, because
 * this filter runs before the host app's authentication filter. A link started by nobody is
 * still sent to the provider and refused at the callback, so the browser always ends up on a
 * page the frontend owns.
 */
@Component
class SocialAuthorizationRequestResolver(
    clients: ClientRegistrationRepository,
    private val accessTokens: AuthenticateAccessTokenQuery,
) : OAuth2AuthorizationRequestResolver {

    private val delegate = DefaultOAuth2AuthorizationRequestResolver(clients, AUTHORIZATION_BASE_URI).apply {
        setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce())
    }

    override fun resolve(request: HttpServletRequest): OAuth2AuthorizationRequest? =
        delegate.resolve(request)?.let { withIntent(it, request) }

    override fun resolve(request: HttpServletRequest, clientRegistrationId: String): OAuth2AuthorizationRequest? =
        delegate.resolve(request, clientRegistrationId)?.let { withIntent(it, request) }

    private fun withIntent(authorizationRequest: OAuth2AuthorizationRequest, request: HttpServletRequest): OAuth2AuthorizationRequest {
        val intent = if (request.getParameter(SocialLoginIntent.PARAMETER) == "link") SocialLoginIntent.LINK else SocialLoginIntent.LOGIN
        val linkingAccount = if (intent == SocialLoginIntent.LINK) signedInAccount(request) else null

        return OAuth2AuthorizationRequest.from(authorizationRequest)
            .attributes { attributes ->
                attributes[SocialLoginIntent.ATTRIBUTE] = intent.name
                if (linkingAccount != null) attributes[SocialLoginIntent.LINKING_ACCOUNT_ATTRIBUTE] = linkingAccount
            }
            .build()
    }

    private fun signedInAccount(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == SessionCookieNames.ACCESS_TOKEN }?.value
            ?.let(accessTokens::authenticate)
            ?.accountId
            ?.toString()

    companion object {
        const val AUTHORIZATION_BASE_URI = "/oauth2/authorization"
    }
}
