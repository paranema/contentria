package com.contentria.analytics.config

import com.contentria.analytics.application.VisitorKeys
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Wires the analytics context into whichever application module hosts it. The host provides a
 * [java.time.Clock] bean, the publishing context's `-api` ports and the `contentria.analytics` settings.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.contentria.analytics")
@EnableJpaRepositories("com.contentria.analytics.domain")
@ComponentScan("com.contentria.analytics.application", "com.contentria.analytics.infra")
@EnableConfigurationProperties(AnalyticsProperties::class)
class AnalyticsCoreConfiguration {

    @Bean
    fun analyticsVisitorKeys(properties: AnalyticsProperties) = VisitorKeys(properties.visitorKeySecret)
}
