package com.contentria.analytics.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * The analytics context's records and statistics: what needs the database and nothing else, so
 * that every process hosting analytics loads it (apps/api, apps/batch). The host provides a
 * [java.time.Clock] bean. Recording visits, which asks publishing, is [AnalyticsWebConfiguration]'s.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.contentria.analytics")
@EnableJpaRepositories("com.contentria.analytics.domain")
@ComponentScan("com.contentria.analytics.application.statistics", "com.contentria.analytics.infra.persistence")
class AnalyticsCoreConfiguration
