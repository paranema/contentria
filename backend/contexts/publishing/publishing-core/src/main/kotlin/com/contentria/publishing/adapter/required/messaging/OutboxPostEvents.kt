package com.contentria.publishing.adapter.required.messaging

import com.contentria.platform.messaging.OutboxWriter
import com.contentria.publishing.api.event.PostDeleted
import com.contentria.publishing.api.event.PostPublished
import com.contentria.publishing.application.required.PostEvents
import com.contentria.publishing.domain.PublishingSchema
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Writes post events to `publishing.outbox_event`. The relay that sends them to Kafka, on topic
 * `publishing.post` keyed by the post id, comes with the first consumer.
 */
@Component
class OutboxPostEvents(jdbc: JdbcTemplate, private val clock: Clock) : PostEvents {

    private val outbox = OutboxWriter(jdbc, PublishingSchema.NAME)

    override fun published(event: PostPublished) {
        outbox.record(AGGREGATE, event.postId, PostPublished.TYPE, PostPublished.VERSION, event, occurredAt = event.publishedAt)
    }

    override fun deleted(event: PostDeleted) {
        outbox.record(AGGREGATE, event.postId, PostDeleted.TYPE, PostDeleted.VERSION, event, occurredAt = clock.instant())
    }

    private companion object {
        /** With the schema, the topic: publishing.post. */
        const val AGGREGATE = "post"
    }
}
