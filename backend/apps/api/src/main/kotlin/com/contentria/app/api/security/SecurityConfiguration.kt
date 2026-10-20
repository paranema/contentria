package com.contentria.app.api.security

import com.contentria.member.api.AuthenticateAccessTokenQuery
import com.contentria.member.config.MemberSocialLogin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Request authentication (docs/conventions/security.md).
 *
 * Stateless: every request carries its access token. CSRF protection is off because the only
 * credential the browser sends automatically is a SameSite=Lax cookie.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        accessTokens: AuthenticateAccessTokenQuery,
        socialLogin: MemberSocialLogin,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/auth/**").permitAll()
                // Blogs and their categories are public; changing them needs a session.
                it.requestMatchers(HttpMethod.GET, "/blogs/**").permitAll()
                // The home page's latest posts across blogs
                it.requestMatchers(HttpMethod.GET, "/posts").permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .addFilterBefore(AccessTokenAuthenticationFilter(accessTokens), UsernamePasswordAuthenticationFilter::class.java)
            // /oauth2/authorization/{provider} and /login/oauth2/code/{provider}, answered by their own filters
            .also(socialLogin::applyTo)
            .build()
}
