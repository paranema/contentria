package com.contentria.notification.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/** The notification context's HTTP endpoints. Loaded by apps/api; all need a signed-in member. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.contentria.notification.presentation.web")
class NotificationWebConfiguration
