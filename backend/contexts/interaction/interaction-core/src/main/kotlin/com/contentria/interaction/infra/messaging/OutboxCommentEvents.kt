package com.contentria.interaction.infra.messaging

import com.contentria.interaction.api.event.CommentAdded
import com.contentria.interaction.domain.InteractionSchema
import com.contentria.platform.messaging.OutboxWriter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Writes comment events to `interaction.outbox_event`, in the transaction that writes the comment.
 * The relay in platform/messaging sends them on topic `interaction.comment`, keyed by the comment id.
 */
@Component
class OutboxCommentEvents(jdbc: JdbcTemplate) {

    private val outbox = OutboxWriter(jdbc, InteractionSchema.NAME)

    fun added(event: CommentAdded) {
        outbox.record(AGGREGATE, event.commentId, CommentAdded.TYPE, CommentAdded.VERSION, event, occurredAt = event.addedAt)
    }

    private companion object {
        /** With the schema, the topic: interaction.comment. */
        const val AGGREGATE = "comment"
    }
}
