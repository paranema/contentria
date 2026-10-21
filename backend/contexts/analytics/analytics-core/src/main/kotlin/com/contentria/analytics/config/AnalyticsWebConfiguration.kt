package com.contentria.analytics.config

import com.contentria.analytics.application.visit.VisitorKeys
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Recording visits over HTTP, the whole of it (docs/contexts/analytics/01-rules.md, 흐름 1): visits
 * only ever come in through the web, so its use case and the translation layer to publishing come
 * with it. Loaded by apps/api, with [AnalyticsCoreConfiguration]; the host provides the publishing
 * context's `-api` ports and `contentria.analytics.visitor-key-secret`, and lets anyone post a visit.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    "com.contentria.analytics.presentation.web",
    "com.contentria.analytics.application.visit",
    "com.contentria.analytics.infra.client",
)
@EnableConfigurationProperties(AnalyticsProperties::class)
class AnalyticsWebConfiguration {

    @Bean
    fun analyticsVisitorKeys(properties: AnalyticsProperties) = VisitorKeys(properties.visitorKeySecret)
}
