package com.contentria.media.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/** The media context's batch jobs. Loaded by apps/batch, with [MediaCoreConfiguration]. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.contentria.media.adapter.provided.batch")
class MediaBatchConfiguration
