package com.contentria.notification.application

import com.contentria.notification.domain.notification.CommentRecipients
import com.contentria.notification.domain.notification.Notification
import com.contentria.notification.infra.persistence.NotificationWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** docs/contexts/notification/01-rules.md, 흐름 2. Everything it needs comes with the event. */
@Service
class CommentNotifier(
    private val writer: NotificationWriter,
    private val clock: Clock,
) {

    @Transactional
    fun notify(
        eventId: UUID,
        commentId: UUID,
        postId: UUID,
        blogId: UUID,
        blogOwnerId: UUID,
        commenterId: UUID,
        parentCommenterId: UUID?,
    ) {
        val at = clock.instant()
        writer.insertAll(
            CommentRecipients.of(commenterId, blogOwnerId, parentCommenterId).map { recipient ->
                Notification.aboutComment(recipient, postId, blogId, commentId, commenterId, eventId, at)
            },
        )
    }
}
