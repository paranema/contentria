package com.contentria.notification.application

import com.contentria.notification.domain.notification.Notification
import com.contentria.notification.domain.notification.NotificationKind
import com.contentria.notification.domain.notification.NotificationRepository
import com.contentria.notification.infra.persistence.NotificationWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** docs/contexts/notification/01-rules.md, 흐름 3. Every method acts on the caller's own notifications. */
@Service
class MyNotificationsService(
    private val notifications: NotificationRepository,
    private val writer: NotificationWriter,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun page(recipientId: UUID, cursor: String?): NotificationPage {
        val after = NotificationCursor.decode(cursor)
        val rows = notifications.findPageFor(recipientId, after.at, after.id)
        val shown = rows.take(NotificationPage.SIZE)
        val next = if (rows.size > NotificationPage.SIZE) shown.last().let { NotificationCursor(it.createdAt, it.id).encode() } else null
        return NotificationPage(shown.map(NotificationView::of), next)
    }

    @Transactional(readOnly = true)
    fun unreadCount(recipientId: UUID): Long = notifications.countByRecipientIdAndReadAtIsNull(recipientId)

    /** 흐름 3의 3~5번 */
    @Transactional
    fun markRead(recipientId: UUID, notificationId: UUID) {
        val notification = notifications.findById(notificationId)?.takeIf { it.recipientId == recipientId }
            ?: throw NotificationException.NotFound()
        notification.markRead(clock.instant())
    }

    /** 흐름 3의 6번 */
    @Transactional
    fun markAllRead(recipientId: UUID) {
        writer.markAllRead(recipientId, clock.instant())
    }
}

data class NotificationPage(val notifications: List<NotificationView>, val nextCursor: String?) {
    companion object {
        const val SIZE = 20
    }
}

/** Ids only: the screen puts titles and names together (01-rules 전제 3). */
data class NotificationView(
    val notificationId: UUID,
    val kind: NotificationKind,
    val postId: UUID,
    val blogId: UUID,
    val commentId: UUID?,
    val actorId: UUID?,
    val createdAt: Instant,
    val read: Boolean,
) {
    companion object {
        fun of(n: Notification) = NotificationView(n.id, n.kind, n.postId, n.blogId, n.commentId, n.actorId, n.createdAt, n.isRead)
    }
}
