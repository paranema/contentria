package com.contentria.member.adapter.provided.web

import com.contentria.member.api.SessionCookieNames
import com.contentria.member.application.provided.IssuedSession
import com.contentria.member.config.MemberProperties
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** docs/conventions/security.md, Cookie Security: HttpOnly, Secure from configuration, SameSite=Lax. */
@Component
class SessionCookies(
    private val properties: MemberProperties,
    private val clock: Clock,
) {

    fun respond(status: ResponseEntity.BodyBuilder, session: IssuedSession): ResponseEntity<SessionResponse> =
        status
            .header(HttpHeaders.SET_COOKIE, *cookiesFor(session))
            .body(SessionResponse(session.accountId, session.accessTokenExpiresAt))

    /** For responses that are redirects rather than bodies, such as the social login callback. */
    fun write(response: HttpServletResponse, session: IssuedSession) {
        cookiesFor(session).forEach { response.addHeader(HttpHeaders.SET_COOKIE, it) }
    }

    private fun cookiesFor(session: IssuedSession): Array<String> = arrayOf(
        cookie(SessionCookieNames.ACCESS_TOKEN, session.accessToken, session.accessTokenExpiresAt),
        cookie(SessionCookieNames.REFRESH_TOKEN, session.refreshToken, session.refreshTokenExpiresAt),
    )

    private fun cookie(name: String, value: String, expiresAt: Instant): String =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(properties.cookie.secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.between(clock.instant(), expiresAt))
            .build()
            .toString()
}
