package com.contentria.notification.presentation.messaging

import com.contentria.interaction.api.event.CommentAdded
import com.contentria.interaction.api.event.InteractionTopics
import com.contentria.notification.application.CommentNotifier
import com.contentria.platform.messaging.EventEnvelope
import com.contentria.platform.messaging.Inbox
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/** Tells the blog's owner and the replied-to commenter of a comment (02-implementation, 이벤트). */
@Component
class CommentAddedConsumer(
    @Qualifier(NotificationInbox.BEAN) private val inbox: Inbox,
    private val comments: CommentNotifier,
) {

    @KafkaListener(topics = [InteractionTopics.COMMENT], groupId = CONSUMER)
    fun on(message: String) {
        val envelope = EventEnvelope.parse(message)
        if (envelope.type != CommentAdded.TYPE || envelope.version != CommentAdded.VERSION) return
        val event = envelope.payloadAs<CommentAdded>()
        inbox.processOnce(envelope.eventId, CONSUMER) {
            comments.notify(
                eventId = envelope.eventId,
                commentId = event.commentId,
                postId = event.postId,
                blogId = event.blogId,
                blogOwnerId = event.blogOwnerId,
                commenterId = event.commenterId,
                parentCommenterId = event.parentCommenterId,
            )
        }
    }

    companion object {
        /** The consumer group, and the consumer's name in the inbox. */
        const val CONSUMER = "notification.comment-added"
    }
}
