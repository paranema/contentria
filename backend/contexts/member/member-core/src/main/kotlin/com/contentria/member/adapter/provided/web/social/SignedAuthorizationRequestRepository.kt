package com.contentria.member.adapter.provided.web.social

import com.contentria.member.config.MemberProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Keeps the pending authorization request (state, nonce, PKCE verifier, intent) in a cookie,
 * because the API holds no HTTP session.
 *
 * The cookie is signed with a key derived from the access-token secret. Without the signature
 * a visitor could rewrite the linking account and attach their own social identity to someone
 * else's account. It is not encrypted: everything in it already sits in the visitor's browser.
 *
 * The request is also kept on the callback request after removal, so the success handler can
 * read the intent ([completed]).
 */
@Component
class SignedAuthorizationRequestRepository(
    private val properties: MemberProperties,
    private val clock: Clock,
) : AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private val key = SecretKeySpec(
        hmac(SecretKeySpec(Base64.getDecoder().decode(properties.accessToken.secret), HMAC), KEY_PURPOSE.toByteArray()),
        HMAC,
    )

    private val json = jacksonObjectMapper()

    override fun loadAuthorizationRequest(request: HttpServletRequest): OAuth2AuthorizationRequest? {
        val state = request.getParameter(OAuth2ParameterNames.STATE) ?: return null
        return read(request)?.takeIf { it.state == state }
    }

    override fun saveAuthorizationRequest(
        authorizationRequest: OAuth2AuthorizationRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(sign(authorizationRequest), MAX_AGE))
    }

    override fun removeAuthorizationRequest(request: HttpServletRequest, response: HttpServletResponse): OAuth2AuthorizationRequest? {
        expire(response)
        val loaded = loadAuthorizationRequest(request) ?: return null
        request.setAttribute(COMPLETED_ATTRIBUTE, loaded)
        return loaded
    }

    private fun sign(request: OAuth2AuthorizationRequest): String {
        val stored = Stored(
            authorizationUri = request.authorizationUri,
            clientId = request.clientId,
            redirectUri = request.redirectUri,
            scopes = request.scopes,
            state = checkNotNull(request.state) { "The default resolver always sets a state" },
            additionalParameters = request.additionalParameters.mapValues { it.value.toString() },
            attributes = request.attributes.mapValues { it.value.toString() },
            authorizationRequestUri = request.authorizationRequestUri,
            issuedAt = clock.instant().epochSecond,
        )
        val payload = BASE64.encodeToString(json.writeValueAsBytes(stored))
        return "$payload.${BASE64.encodeToString(hmac(key, payload.toByteArray()))}"
    }

    private fun read(request: HttpServletRequest): OAuth2AuthorizationRequest? {
        val value = request.cookies?.firstOrNull { it.name == COOKIE }?.value ?: return null
        val payload = value.substringBefore('.', missingDelimiterValue = "")
        val signature = runCatching { Base64.getUrlDecoder().decode(value.substringAfter('.')) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(hmac(key, payload.toByteArray()), signature)) return null

        val stored = runCatching { json.readValue<Stored>(Base64.getUrlDecoder().decode(payload)) }.getOrNull() ?: return null
        if (clock.instant().epochSecond - stored.issuedAt > MAX_AGE.seconds) return null

        return OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri(stored.authorizationUri)
            .clientId(stored.clientId)
            .redirectUri(stored.redirectUri)
            .scopes(stored.scopes)
            .state(stored.state)
            .additionalParameters(stored.additionalParameters)
            .attributes(stored.attributes)
            .authorizationRequestUri(stored.authorizationRequestUri)
            .build()
    }

    private fun expire(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
    }

    private fun cookie(value: String, maxAge: Duration): String =
        ResponseCookie.from(COOKIE, value)
            .httpOnly(true)
            .secure(properties.cookie.secure)
            // Lax: the provider sends the browser back with a top-level GET, which Lax allows.
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()
            .toString()

    /** Only the fields a code-flow request with OIDC and PKCE carries. Every value is a string. */
    private data class Stored(
        val authorizationUri: String,
        val clientId: String,
        val redirectUri: String?,
        val scopes: Set<String>,
        val state: String,
        val additionalParameters: Map<String, String>,
        val attributes: Map<String, String>,
        val authorizationRequestUri: String,
        val issuedAt: Long,
    )

    companion object {
        private const val COOKIE = "oauth2AuthRequest"
        private const val COMPLETED_ATTRIBUTE = "com.contentria.member.oauth2.completed-authorization-request"
        private const val HMAC = "HmacSHA256"

        /** Separates this key from the access-token signing key, though both come from one secret. */
        private const val KEY_PURPOSE = "contentria.member.oauth2-authorization-request"

        private val MAX_AGE: Duration = Duration.ofMinutes(10)
        private val BASE64 = Base64.getUrlEncoder().withoutPadding()

        /** The authorization request this callback completed, if the repository removed one. */
        fun completed(request: HttpServletRequest): OAuth2AuthorizationRequest? =
            request.getAttribute(COMPLETED_ATTRIBUTE) as? OAuth2AuthorizationRequest

        private fun hmac(key: SecretKeySpec, data: ByteArray): ByteArray =
            Mac.getInstance(HMAC).apply { init(key) }.doFinal(data)
    }
}
