package com.contentria.app.api

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * The one application every API integration test runs against. Spring caches a context by its
 * configuration, and a @DynamicPropertySource method is part of it: declared here once, every
 * subclass gets the same key and so the same context, pool, Kafka consumers and outbox relay.
 * A subclass must not add its own @DynamicPropertySource, @Import or @MockitoBean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RecordingSenders::class)
abstract class ApiIntegrationTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = ApiTestEnvironment.register(registry)
    }
}
