package com.contentria.member.config

import com.contentria.member.application.service.SessionPolicy
import com.contentria.member.domain.profile.DisplayNameGenerator
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Wires the member context into whichever application module hosts it: entities, repositories,
 * use cases and outbound adapters. Inbound channels have their own configuration classes
 * ([MemberWebConfiguration]) so an app loads only the ones it serves.
 *
 * The host app provides a [java.time.Clock] bean.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.contentria.member")
@EnableJpaRepositories("com.contentria.member.application.required")
@ComponentScan("com.contentria.member.application", "com.contentria.member.adapter.required")
@EnableConfigurationProperties(MemberProperties::class)
class MemberCoreConfiguration {

    @Bean
    fun memberSessionPolicy(properties: MemberProperties) = SessionPolicy(properties.refreshTokenTtl)

    @Bean
    fun memberDisplayNameGenerator() = DisplayNameGenerator()
}
