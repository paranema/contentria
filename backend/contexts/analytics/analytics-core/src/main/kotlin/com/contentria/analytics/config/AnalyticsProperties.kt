package com.contentria.analytics.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("contentria.analytics")
data class AnalyticsProperties(
    /** At least 32 characters. Changing it changes every visitor's key from then on. No default. */
    val visitorKeySecret: String,
)
