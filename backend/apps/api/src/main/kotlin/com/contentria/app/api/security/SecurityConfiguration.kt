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
                // Spring forwards a request it could not serve (no such path, an unhandled failure) to
                // /error, and the access-token filter does not run again on that forward. Securing
                // /error would turn every such 404 or 500 into a 401, which a client reads as a
                // lost session. The request itself was already authorized before the forward.
                it.requestMatchers("/error").permitAll()
                it.requestMatchers("/auth/**").permitAll()
                // Blogs and their categories are public; changing them needs a session.
                it.requestMatchers(HttpMethod.GET, "/blogs/**").permitAll()
                // The home page's latest posts across blogs
                it.requestMatchers(HttpMethod.GET, "/posts").permitAll()
                // Like and subscriber counts, which anyone may see
                it.requestMatchers(HttpMethod.GET, "/interactions/counts").permitAll()
                // A published post's comments
                it.requestMatchers(HttpMethod.GET, "/posts/*/comments").permitAll()
                // A reader opening a blog or a post, signed in or not
                it.requestMatchers(HttpMethod.POST, "/analytics/visits").permitAll()
                // A published post's video, for its page
                it.requestMatchers(HttpMethod.GET, "/posts/*/video").permitAll()
                // Members' public profiles: who wrote a post or a comment
                it.requestMatchers(HttpMethod.GET, "/members/profiles").permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .addFilterBefore(AccessTokenAuthenticationFilter(accessTokens), UsernamePasswordAuthenticationFilter::class.java)
            // /oauth2/authorization/{provider} and /login/oauth2/code/{provider}, answered by their own filters
            .also(socialLogin::applyTo)
            .build()
}
