package com.contentria.notification.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Wires the notification context into whichever application module hosts it. The host provides
 * a [java.time.Clock] bean and the interaction context's `-api` ports.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.contentria.notification")
@EnableJpaRepositories("com.contentria.notification.domain")
@ComponentScan("com.contentria.notification.application", "com.contentria.notification.infra")
class NotificationCoreConfiguration
