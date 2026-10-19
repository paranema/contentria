package com.contentria.member.adapter.provided.web

import com.contentria.member.api.SessionCookieNames
import com.contentria.member.application.provided.IssuedSession
import com.contentria.member.config.MemberProperties
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
            .header(HttpHeaders.SET_COOKIE, cookie(SessionCookieNames.ACCESS_TOKEN, session.accessToken, session.accessTokenExpiresAt))
            .header(HttpHeaders.SET_COOKIE, cookie(SessionCookieNames.REFRESH_TOKEN, session.refreshToken, session.refreshTokenExpiresAt))
            .body(SessionResponse(session.accountId, session.accessTokenExpiresAt))

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
