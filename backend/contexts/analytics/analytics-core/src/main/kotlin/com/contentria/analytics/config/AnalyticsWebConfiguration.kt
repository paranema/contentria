package com.contentria.analytics.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/** The analytics context's HTTP endpoints. Loaded by apps/api, whose filter chain lets anyone record a visit. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.contentria.analytics.presentation.web")
class AnalyticsWebConfiguration
