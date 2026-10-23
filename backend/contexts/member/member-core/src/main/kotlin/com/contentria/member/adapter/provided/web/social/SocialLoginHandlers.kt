package com.contentria.member.adapter.provided.web.social

import com.contentria.member.adapter.provided.web.SessionCookies
import com.contentria.member.application.provided.LinkSocialProviderUseCase
import com.contentria.member.application.provided.LoginWithSocialProviderUseCase
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.VerifiedSocialIdentity
import com.contentria.member.config.MemberProperties
import com.contentria.member.domain.account.AccountException
import com.contentria.member.domain.account.AuthProvider
import com.contentria.shared.exception.DomainException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.StandardClaimNames
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * The provider has sent the browser back and Spring Security has checked the ID token. This
 * turns the token into a [VerifiedSocialIdentity], runs 흐름 2 or 흐름 3, and redirects to the
 * frontend. Failures become a predefined code in the query string, never an exception message
 * (docs/conventions/security.md, 소셜 로그인 실패).
 */
@Component
class SocialLoginSuccessHandler(
    private val login: LoginWithSocialProviderUseCase,
    private val link: LinkSocialProviderUseCase,
    private val cookies: SessionCookies,
    private val redirects: SocialLoginRedirects,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
        val authorizationRequest = SignedAuthorizationRequestRepository.completed(request)
        val intent = SocialLoginIntent.of(authorizationRequest)

        val target = try {
            val identity = identityOf(authentication as OAuth2AuthenticationToken)
            when (intent) {
                SocialLoginIntent.LOGIN -> {
                    cookies.write(response, login.login(identity))
                    redirects.loginSucceeded()
                }
                SocialLoginIntent.LINK -> {
                    val accountId = SocialLoginIntent.linkingAccountOf(authorizationRequest)
                        ?: throw MemberUseCaseException.InvalidSession()
                    link.link(accountId, identity)
                    redirects.linked()
                }
            }
        } catch (e: DomainException) {
            redirects.failed(intent, errorCodeOf(e))
        } catch (e: DataIntegrityViolationException) {
            // Two requests raced past the application checks and a unique constraint caught the second.
            redirects.failed(intent, FAILED)
        }
        response.sendRedirect(target)
    }

    /** Claims come from the ID token only, not from the userinfo response (01-rules 흐름 2). */
    private fun identityOf(token: OAuth2AuthenticationToken): VerifiedSocialIdentity {
        val provider = AuthProvider.entries.single { !it.usesPassword && it.name.equals(token.authorizedClientRegistrationId, ignoreCase = true) }
        val idToken = (token.principal as OidcUser).idToken
        return VerifiedSocialIdentity(
            provider = provider,
            providerUserId = checkNotNull(idToken.subject) { "An ID token always carries sub" },
            email = idToken.email,
            emailVerified = idToken.getClaimAsBoolean(StandardClaimNames.EMAIL_VERIFIED) == true,
        )
    }

    private fun errorCodeOf(e: DomainException): String = when (e) {
        is MemberUseCaseException.SocialEmailMissing -> "SOCIAL_EMAIL_MISSING"
        is AccountException.EmailNotVerifiedByProvider -> "SOCIAL_EMAIL_NOT_VERIFIED"
        is AccountException.NotAuthenticatable -> "ACCOUNT_NOT_ACTIVE"
        is AccountException.ProviderAlreadyLinked -> "PROVIDER_ALREADY_LINKED"
        is MemberUseCaseException.SocialIdentityLinkedElsewhere -> "SOCIAL_IDENTITY_TAKEN"
        is MemberUseCaseException.InvalidSession -> "SIGN_IN_REQUIRED"
        else -> FAILED
    }

    private companion object {
        const val FAILED = "SOCIAL_LOGIN_FAILED"
    }
}

/**
 * Spring Security could not complete the exchange: the user declined at the provider, the
 * state did not match a stored request, or the ID token failed a check.
 */
@Component
class SocialLoginFailureHandler(private val redirects: SocialLoginRedirects) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(request: HttpServletRequest, response: HttpServletResponse, exception: AuthenticationException) {
        val intent = SocialLoginIntent.of(SignedAuthorizationRequestRepository.completed(request))
        response.sendRedirect(redirects.failed(intent, "SOCIAL_LOGIN_FAILED"))
    }
}

/** Every destination is configured; nothing from the request goes into the URL but a fixed code. */
@Component
class SocialLoginRedirects(private val properties: MemberProperties) {

    fun loginSucceeded(): String = properties.social.loginSuccessUrl

    fun linked(): String = withQuery(properties.social.linkResultUrl, "linked", "true")

    fun failed(intent: SocialLoginIntent, code: String): String = when (intent) {
        SocialLoginIntent.LOGIN -> withQuery(properties.social.loginFailureUrl, "error", code)
        SocialLoginIntent.LINK -> withQuery(properties.social.linkResultUrl, "error", code)
    }

    private fun withQuery(url: String, name: String, value: String): String =
        UriComponentsBuilder.fromUriString(url).queryParam(name, value).build().toUriString()
}
