package com.contentria.app.api.security

import com.contentria.member.api.AuthenticateAccessTokenQuery
import com.contentria.member.api.SessionCookieNames
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Reads the access token from the Authorization header or the access-token cookie and, when the
 * member context accepts it, makes the account id the request's principal.
 *
 * An invalid token is not an error here: the request simply stays anonymous, and the
 * authorization rules decide whether that is enough.
 */
class AccessTokenAuthenticationFilter(
    private val accessTokens: AuthenticateAccessTokenQuery,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = bearerToken(request) ?: cookieToken(request)
        val account = token?.let(accessTokens::authenticate)
        if (account != null) {
            val authorities = account.roles.map { SimpleGrantedAuthority("ROLE_$it") }
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(account.accountId, null, authorities)
        }
        chain.doFilter(request, response)
    }

    private fun bearerToken(request: HttpServletRequest): String? =
        request.getHeader(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER) }
            ?.removePrefix(BEARER)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun cookieToken(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == SessionCookieNames.ACCESS_TOKEN }?.value

    private companion object {
        const val BEARER = "Bearer "
    }
}
