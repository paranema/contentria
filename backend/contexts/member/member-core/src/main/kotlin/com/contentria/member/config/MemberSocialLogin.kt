package com.contentria.member.config

import com.contentria.member.adapter.provided.web.social.SignedAuthorizationRequestRepository
import com.contentria.member.adapter.provided.web.social.SocialAuthorizationRequestResolver
import com.contentria.member.adapter.provided.web.social.SocialLoginFailureHandler
import com.contentria.member.adapter.provided.web.social.SocialLoginSuccessHandler
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository

/**
 * Spring Security's OAuth2 login, wired to the member context. The host app's filter chain
 * calls [applyTo]; the app sees this class and nothing of the adapters behind it.
 *
 * | Endpoint | |
 * |---|---|
 * | `GET /oauth2/authorization/{provider}` | Redirects to the provider. `?intent=link` links instead of signing in |
 * | `GET /login/oauth2/code/{provider}` | The provider's callback |
 */
class MemberSocialLogin internal constructor(
    private val resolver: SocialAuthorizationRequestResolver,
    private val requests: SignedAuthorizationRequestRepository,
    private val success: SocialLoginSuccessHandler,
    private val failure: SocialLoginFailureHandler,
) {

    fun applyTo(http: HttpSecurity) {
        http.oauth2Login {
            // Any custom value turns off the generated login page. The host's entry point still
            // answers unauthenticated requests with 401.
            it.loginPage(LOGIN_PAGE)
            it.authorizationEndpoint { endpoint ->
                endpoint.authorizationRequestResolver(resolver)
                endpoint.authorizationRequestRepository(requests)
            }
            it.authorizedClientRepository(DiscardingAuthorizedClientRepository)
            // The ID token is all the member context reads; the userinfo endpoint is never called.
            it.userInfoEndpoint { endpoint -> endpoint.oidcUserService(OidcUserService().apply { setRetrieveUserInfo { false } }) }
            it.successHandler(success)
            it.failureHandler(failure)
        }
    }

    /**
     * The provider's access token is never used after sign-in. The default repository would keep
     * one per login in memory for as long as the process lives.
     */
    private object DiscardingAuthorizedClientRepository : OAuth2AuthorizedClientRepository {
        override fun <T : OAuth2AuthorizedClient> loadAuthorizedClient(
            clientRegistrationId: String,
            principal: Authentication,
            request: HttpServletRequest,
        ): T? = null

        override fun saveAuthorizedClient(
            authorizedClient: OAuth2AuthorizedClient,
            principal: Authentication,
            request: HttpServletRequest,
            response: HttpServletResponse,
        ) = Unit

        override fun removeAuthorizedClient(
            clientRegistrationId: String,
            principal: Authentication,
            request: HttpServletRequest,
            response: HttpServletResponse,
        ) = Unit
    }

    private companion object {
        const val LOGIN_PAGE = "/auth/login"
    }
}
