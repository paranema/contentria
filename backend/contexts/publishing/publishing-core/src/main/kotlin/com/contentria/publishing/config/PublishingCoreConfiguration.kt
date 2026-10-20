package com.contentria.publishing.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Wires the publishing context into whichever application module hosts it: entities, use cases
 * and outbound adapters. Inbound channels get their own configuration classes when they exist.
 *
 * The host app provides a [java.time.Clock] bean and the member context's `-api` ports.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.contentria.publishing")
@EnableJpaRepositories("com.contentria.publishing.application.required")
@ComponentScan("com.contentria.publishing.application", "com.contentria.publishing.adapter.required")
class PublishingCoreConfiguration
