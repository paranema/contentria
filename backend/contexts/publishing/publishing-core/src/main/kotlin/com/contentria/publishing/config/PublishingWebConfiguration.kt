package com.contentria.publishing.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * The publishing context's HTTP endpoints. Loaded by apps/api, whose filter chain lets anyone
 * read (any GET under `/blogs/`) and requires a session for everything else.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.contentria.publishing.adapter.provided.web")
class PublishingWebConfiguration
