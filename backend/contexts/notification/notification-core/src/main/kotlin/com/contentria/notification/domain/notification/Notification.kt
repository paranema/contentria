package com.contentria.notification.domain.notification

import com.contentria.notification.domain.NotificationSchema
import com.contentria.shared.id.Ids
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * One member's record of something that happened elsewhere (docs/contexts/notification/01-rules.md).
 * Each notification is its own aggregate: recipients read theirs apart.
 *
 * Only ids: the post's title and the commenter's name are put together by the screen, so a
 * renamed post or member shows as it is now, and a post nobody can see drops out (01-rules, 왜).
 */
@Entity
@Table(
    name = "notifications",
    schema = NotificationSchema.NAME,
    uniqueConstraints = [UniqueConstraint(name = "uq_notifications_event_recipient", columnNames = ["source_event_id", "recipient_id"])],
)
class Notification private constructor(
    id: UUID,
    recipientId: UUID,
    kind: NotificationKind,
    postId: UUID,
    blogId: UUID,
    commentId: UUID?,
    actorId: UUID?,
    sourceEventId: UUID,
    createdAt: Instant,
) {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @Column(name = "recipient_id", nullable = false, updatable = false)
    val recipientId: UUID = recipientId

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 30)
    val kind: NotificationKind = kind

    @Column(name = "post_id", nullable = false, updatable = false)
    val postId: UUID = postId

    @Column(name = "blog_id", nullable = false, updatable = false)
    val blogId: UUID = blogId

    /** The comment or reply; null for a new post. */
    @Column(name = "comment_id", updatable = false)
    val commentId: UUID? = commentId

    /** Who wrote the comment or reply; null for a new post, whose blog says who wrote it. */
    @Column(name = "actor_id", updatable = false)
    val actorId: UUID? = actorId

    /** The event this came from: one notification per event and recipient. */
    @Column(name = "source_event_id", nullable = false, updatable = false)
    val sourceEventId: UUID = sourceEventId

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = createdAt

    @Column(name = "read_at")
    var readAt: Instant? = null
        protected set

    val isRead: Boolean get() = readAt != null

    /** 흐름 3의 4, 5번. Reading twice keeps the first time. */
    fun markRead(at: Instant) {
        if (readAt == null) readAt = at
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Notification && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {

        /** 흐름 1: one for each subscriber of the blog. */
        fun newPost(recipientId: UUID, postId: UUID, blogId: UUID, sourceEventId: UUID, at: Instant) =
            Notification(Ids.newId(), recipientId, NotificationKind.NEW_POST, postId, blogId, null, null, sourceEventId, at)

        /** 흐름 2: one for each of [CommentRecipients]. */
        fun aboutComment(
            recipient: CommentRecipient,
            postId: UUID,
            blogId: UUID,
            commentId: UUID,
            actorId: UUID,
            sourceEventId: UUID,
            at: Instant,
        ) = Notification(Ids.newId(), recipient.accountId, recipient.kind, postId, blogId, commentId, actorId, sourceEventId, at)
    }
}
