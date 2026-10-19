package com.contentria.member.config

import com.contentria.member.adapter.provided.web.social.SignedAuthorizationRequestRepository
import com.contentria.member.adapter.provided.web.social.SocialAuthorizationRequestResolver
import com.contentria.member.adapter.provided.web.social.SocialLoginFailureHandler
import com.contentria.member.adapter.provided.web.social.SocialLoginSuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * The member context's HTTP endpoints and its social login callback. Loaded by apps/api, which
 * must configure an OAuth2 client registration for every social provider.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.contentria.member.adapter.provided.web")
class MemberWebConfiguration {

    @Bean
    fun memberSocialLogin(
        resolver: SocialAuthorizationRequestResolver,
        requests: SignedAuthorizationRequestRepository,
        success: SocialLoginSuccessHandler,
        failure: SocialLoginFailureHandler,
    ) = MemberSocialLogin(resolver, requests, success, failure)
}
