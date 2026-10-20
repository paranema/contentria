package com.contentria.publishing.config

import com.contentria.platform.messaging.OutboxSource
import com.contentria.publishing.domain.PublishingSchema
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
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
class PublishingCoreConfiguration {

    /** `publishing.outbox_event`, for the relay (PostPublished, PostDeleted). */
    @Bean
    fun publishingOutbox() = OutboxSource(PublishingSchema.NAME)
}
