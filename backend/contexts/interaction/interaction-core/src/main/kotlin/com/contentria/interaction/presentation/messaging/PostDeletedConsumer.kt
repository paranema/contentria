package com.contentria.interaction.presentation.messaging

import com.contentria.interaction.application.PostCleanupService
import com.contentria.platform.messaging.EventEnvelope
import com.contentria.platform.messaging.Inbox
import com.contentria.publishing.api.event.PostDeleted
import com.contentria.publishing.api.event.PublishingTopics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Removes a deleted post's comments and likes (02-implementation, 이벤트). Other events on the
 * topic, and versions this consumer does not know, pass by: during a version change both are
 * published, and this one reads its own.
 */
@Component
class PostDeletedConsumer(
    @Qualifier(InteractionInbox.BEAN) private val inbox: Inbox,
    private val cleanup: PostCleanupService,
) {

    @KafkaListener(topics = [PublishingTopics.POST], groupId = CONSUMER)
    fun on(message: String) {
        val envelope = EventEnvelope.parse(message)
        if (envelope.type != PostDeleted.TYPE || envelope.version != PostDeleted.VERSION) return
        inbox.processOnce(envelope.eventId, CONSUMER) {
            cleanup.removeAllFor(envelope.payloadAs<PostDeleted>().postId)
        }
    }

    companion object {
        /** The consumer group, and the consumer's name in the inbox. */
        const val CONSUMER = "interaction.post-deleted"
    }
}
