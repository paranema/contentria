package com.contentria.notification.infra.persistence

import com.contentria.notification.domain.notification.Notification
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Writes many notifications at once with JDBC: new ones in batches, in the transaction already running
 * (docs/contexts/notification/02-implementation.md, 이벤트). A new post can make one row per
 * subscriber: JPA would read each assigned-id entity back before inserting it.
 *
 * A row the unique constraint already holds (same event, same recipient) is skipped: the second
 * line of defence after the inbox.
 */
@Component
class NotificationWriter(private val jdbc: JdbcTemplate) {

    fun insertAll(notifications: List<Notification>) {
        if (notifications.isEmpty()) return
        jdbc.batchUpdate(INSERT, notifications, BATCH_SIZE) { statement, n ->
            statement.setObject(1, n.id)
            statement.setObject(2, n.recipientId)
            statement.setString(3, n.kind.name)
            statement.setObject(4, n.postId)
            statement.setObject(5, n.blogId)
            statement.setObject(6, n.commentId)
            statement.setObject(7, n.actorId)
            statement.setObject(8, n.sourceEventId)
            statement.setTimestamp(9, Timestamp.from(n.createdAt))
        }
    }

    /** 흐름 3의 6번: every notification of [recipientId] still unread now. One statement. */
    fun markAllRead(recipientId: UUID, at: Instant): Int =
        jdbc.update(
            "UPDATE notification.notifications SET read_at = ? WHERE recipient_id = ? AND read_at IS NULL",
            Timestamp.from(at), recipientId,
        )

    /** Removes notifications made before [before], read or not (01-rules, 보존 기간). */
    fun deleteMadeBefore(before: Instant): Int =
        jdbc.update("DELETE FROM notification.notifications WHERE created_at < ?", Timestamp.from(before))

    private companion object {
        const val BATCH_SIZE = 500
        const val INSERT = "INSERT INTO notification.notifications " +
            "(id, recipient_id, kind, post_id, blog_id, comment_id, actor_id, source_event_id, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT ON CONSTRAINT uq_notifications_event_recipient DO NOTHING"
    }
}
