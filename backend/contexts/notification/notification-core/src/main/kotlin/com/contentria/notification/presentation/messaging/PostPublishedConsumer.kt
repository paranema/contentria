package com.contentria.notification.presentation.messaging

import com.contentria.notification.application.NewPostNotifier
import com.contentria.platform.messaging.EventEnvelope
import com.contentria.platform.messaging.Inbox
import com.contentria.publishing.api.event.PostPublished
import com.contentria.publishing.api.event.PublishingTopics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Tells a blog's subscribers of its new post (02-implementation, 이벤트). Other events on the topic,
 * and versions this consumer does not know, pass by.
 */
@Component
class PostPublishedConsumer(
    @Qualifier(NotificationInbox.BEAN) private val inbox: Inbox,
    private val newPosts: NewPostNotifier,
) {

    @KafkaListener(topics = [PublishingTopics.POST], groupId = CONSUMER)
    fun on(message: String) {
        val envelope = EventEnvelope.parse(message)
        if (envelope.type != PostPublished.TYPE || envelope.version != PostPublished.VERSION) return
        val event = envelope.payloadAs<PostPublished>()
        // Interaction is asked before the inbox's transaction opens; a repeat asks again and is then dropped.
        val subscribers = newPosts.subscribersOf(event.blogId)
        inbox.processOnce(envelope.eventId, CONSUMER) {
            newPosts.notify(envelope.eventId, event.postId, event.blogId, subscribers)
        }
    }

    companion object {
        /** The consumer group, and the consumer's name in the inbox. */
        const val CONSUMER = "notification.post-published"
    }
}
