package com.contentria.interaction.config

import com.contentria.interaction.domain.InteractionSchema
import com.contentria.platform.messaging.OutboxSource
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Wires the interaction context into whichever application module hosts it. The host provides
 * a [java.time.Clock] bean and the member and publishing contexts' `-api` ports.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.contentria.interaction")
@EnableJpaRepositories("com.contentria.interaction.domain")
@ComponentScan("com.contentria.interaction.application", "com.contentria.interaction.infra")
class InteractionCoreConfiguration {

    /** `interaction.outbox_event`, for the relay (CommentAdded). */
    @Bean
    fun interactionOutbox() = OutboxSource(InteractionSchema.NAME)
}
