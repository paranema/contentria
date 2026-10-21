package com.contentria.notification.infra.persistence

import com.contentria.notification.domain.notification.Notification
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp

/**
 * Inserts new notifications in JDBC batches, in the transaction already running
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

    private companion object {
        const val BATCH_SIZE = 500
        const val INSERT = "INSERT INTO notification.notifications " +
            "(id, recipient_id, kind, post_id, blog_id, comment_id, actor_id, source_event_id, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT ON CONSTRAINT uq_notifications_event_recipient DO NOTHING"
    }
}
