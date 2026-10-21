package com.contentria.analytics.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/** The analytics context's batch jobs. Loaded by apps/batch, with [AnalyticsCoreConfiguration]. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.contentria.analytics.presentation.batch")
class AnalyticsBatchConfiguration
