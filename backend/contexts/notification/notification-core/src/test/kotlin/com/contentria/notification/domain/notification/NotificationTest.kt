package com.contentria.notification.domain.notification

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class NotificationTest {

    private val at = Instant.parse("2026-10-21T00:00:00Z")

    @Test
    @DisplayName("새 글 알림은 글과 블로그만 담고, 댓글과 쓴 사람은 없다")
    fun `a new post`() {
        val recipient = UUID.randomUUID()
        val post = UUID.randomUUID()
        val blog = UUID.randomUUID()
        val event = UUID.randomUUID()

        val notification = Notification.newPost(recipient, post, blog, event, at)

        assertThat(notification.kind).isEqualTo(NotificationKind.NEW_POST)
        assertThat(listOf(notification.recipientId, notification.postId, notification.blogId, notification.sourceEventId))
            .containsExactly(recipient, post, blog, event)
        assertThat(notification.commentId).isNull()
        assertThat(notification.actorId).isNull()
        assertThat(notification.createdAt).isEqualTo(at)
        assertThat(notification.isRead).isFalse()
    }

    @Test
    @DisplayName("댓글 알림은 받는 사람의 종류대로 댓글과 쓴 사람을 담는다")
    fun `about a comment`() {
        val recipient = CommentRecipient(UUID.randomUUID(), NotificationKind.REPLY_TO_COMMENT)
        val comment = UUID.randomUUID()
        val actor = UUID.randomUUID()

        val notification = Notification.aboutComment(recipient, UUID.randomUUID(), UUID.randomUUID(), comment, actor, UUID.randomUUID(), at)

        assertThat(notification.recipientId).isEqualTo(recipient.accountId)
        assertThat(notification.kind).isEqualTo(NotificationKind.REPLY_TO_COMMENT)
        assertThat(notification.commentId).isEqualTo(comment)
        assertThat(notification.actorId).isEqualTo(actor)
    }

    @Test
    @DisplayName("댓글로는 새 글 알림을 만들 수 없다")
    fun `a comment is not a new post`() {
        assertThatThrownBy { CommentRecipient(UUID.randomUUID(), NotificationKind.NEW_POST) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @DisplayName("두 번 읽어도 처음 읽은 시각을 유지한다")
    fun `reading twice keeps the first time`() {
        val notification = Notification.newPost(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), at)

        notification.markRead(at.plusSeconds(60))
        notification.markRead(at.plusSeconds(120))

        assertThat(notification.isRead).isTrue()
        assertThat(notification.readAt).isEqualTo(at.plusSeconds(60))
    }
}
