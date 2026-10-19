package com.contentria.member.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("contentria.member")
data class MemberProperties(
    val accessToken: AccessToken,
    val refreshTokenTtl: Duration = Duration.ofDays(7),
    val cookie: Cookie = Cookie(),
    val mail: Mail = Mail(),
) {
    /** [secret] is a Base64-encoded key of at least 256 bits. */
    data class AccessToken(val secret: String, val ttl: Duration = Duration.ofMinutes(15))

    /** docs/conventions/security.md: Secure comes from configuration, never from the request. */
    data class Cookie(val secure: Boolean = true)

    data class Mail(
        val mode: String = "smtp",
        val from: String = "no-reply@contentria.com",
        val codeValidityNotice: String = "코드는 10분 동안 유효합니다.",
    )
}
