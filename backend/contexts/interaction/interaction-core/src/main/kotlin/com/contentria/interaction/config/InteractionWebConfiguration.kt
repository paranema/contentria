package com.contentria.interaction.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/** The interaction context's HTTP endpoints. Loaded by apps/api. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.contentria.interaction.presentation.web")
class InteractionWebConfiguration
