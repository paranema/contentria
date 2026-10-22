package com.contentria.member.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("contentria.member")
data class MemberProperties(
    val accessToken: AccessToken,
    val refreshTokenTtl: Duration = Duration.ofDays(7),
    val cookie: Cookie = Cookie(),
    val mail: Mail = Mail(),
    val social: Social = Social(),
    val password: Password = Password(),
    val recaptcha: Recaptcha = Recaptcha(),
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

    /**
     * docs/contexts/member/01-rules.md, 사람 확인. [mode] `google` everywhere deployed; `off` for
     * local development only, where the frontend has no site keys. The secrets have no default
     * and must be set when the mode is `google`.
     */
    data class Recaptcha(
        val mode: String = "google",
        val v3SecretKey: String = "",
        val v2SecretKey: String = "",
        val minimumScore: Double = 0.5,
        val verifyUrl: URI = URI.create("https://www.google.com/recaptcha/api/siteverify"),
    ) {
        val required: Boolean get() = mode != "off"
    }

    /** The frontend page that reads `#token=` from a setup or reset link. */
    data class Password(val linkUrl: String = "http://localhost:3000/password")

    /**
     * Where the browser lands after a social sign-in or link. Configured, never read from the
     * request, so the callback cannot be turned into an open redirect.
     */
    data class Social(
        val loginSuccessUrl: String = "http://localhost:3000/login/callback",
        val loginFailureUrl: String = "http://localhost:3000/login",
        val linkResultUrl: String = "http://localhost:3000/settings/account",
    )
}
