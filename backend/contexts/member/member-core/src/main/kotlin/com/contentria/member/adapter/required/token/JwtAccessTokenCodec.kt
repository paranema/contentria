package com.contentria.member.adapter.required.token

import com.contentria.member.application.required.AccessTokenCodec
import com.contentria.member.application.required.IssuedAccessToken
import com.contentria.member.application.required.VerifiedAccessToken
import com.contentria.member.config.MemberProperties
import com.contentria.member.domain.account.Role
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * HS256-signed JWT access tokens (docs/conventions/security.md, Token Strategy).
 *
 * Only this context signs and verifies them, so a shared secret is enough. If a context ever
 * runs in another service and must verify tokens itself, switch to an asymmetric key so the
 * verifier cannot also sign.
 */
@Component
class JwtAccessTokenCodec(properties: MemberProperties) : AccessTokenCodec {

    private val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.accessToken.secret))
    private val ttl = properties.accessToken.ttl

    override fun issue(accountId: UUID, roles: Set<Role>, issuedAt: Instant): IssuedAccessToken {
        val expiresAt = issuedAt.plus(ttl)
        val value = Jwts.builder()
            .subject(accountId.toString())
            .claim(ROLES, roles.map { it.name })
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
        return IssuedAccessToken(value, expiresAt)
    }

    override fun verify(token: String, now: Instant): VerifiedAccessToken? = try {
        val claims = Jwts.parser()
            .verifyWith(key)
            .clock { Date.from(now) }
            .build()
            .parseSignedClaims(token)
            .payload
        val roles = (claims[ROLES] as? List<*>).orEmpty()
            .mapNotNull { name -> Role.entries.firstOrNull { it.name == name } }
            .toSet()
        VerifiedAccessToken(UUID.fromString(claims.subject), roles)
    } catch (e: JwtException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        const val ROLES = "roles"
    }
}
