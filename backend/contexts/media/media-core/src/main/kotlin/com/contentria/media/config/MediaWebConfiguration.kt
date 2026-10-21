package com.contentria.media.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/** The media context's HTTP endpoints. Loaded by apps/api; all need a signed-in member. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.contentria.media.adapter.provided.web")
class MediaWebConfiguration
