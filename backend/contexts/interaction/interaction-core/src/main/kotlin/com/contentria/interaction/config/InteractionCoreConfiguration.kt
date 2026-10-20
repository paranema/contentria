package com.contentria.interaction.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
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
class InteractionCoreConfiguration
