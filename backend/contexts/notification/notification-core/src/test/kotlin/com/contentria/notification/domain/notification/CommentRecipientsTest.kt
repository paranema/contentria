package com.contentria.notification.domain.notification

import com.contentria.notification.domain.notification.NotificationKind.COMMENT_ON_POST
import com.contentria.notification.domain.notification.NotificationKind.REPLY_TO_COMMENT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/** The examples of 01-rules 흐름 2: B owns the blog, A and C are readers. */
class CommentRecipientsTest {

    private val owner = UUID.randomUUID()
    private val reader = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test
    @DisplayName("독자가 댓글을 쓰면 블로그 소유자가 내 글의 댓글을 받는다")
    fun `a reader's comment`() {
        assertThat(CommentRecipients.of(reader, owner, null)).containsExactly(CommentRecipient(owner, COMMENT_ON_POST))
    }

    @Test
    @DisplayName("블로그 소유자가 자기 글에 댓글을 쓰면 아무도 받지 않는다")
    fun `the owner's own comment`() {
        assertThat(CommentRecipients.of(owner, owner, null)).isEmpty()
    }

    @Test
    @DisplayName("독자의 댓글에 다른 독자가 답하면 댓글 쓴 사람은 답글을, 블로그 소유자는 댓글을 받는다")
    fun `a reply between readers`() {
        assertThat(CommentRecipients.of(other, owner, reader))
            .containsExactly(CommentRecipient(reader, REPLY_TO_COMMENT), CommentRecipient(owner, COMMENT_ON_POST))
    }

    @Test
    @DisplayName("블로그 소유자가 독자의 댓글에 답하면 댓글 쓴 사람만 받는다")
    fun `the owner replies`() {
        assertThat(CommentRecipients.of(owner, owner, reader)).containsExactly(CommentRecipient(reader, REPLY_TO_COMMENT))
    }

    @Test
    @DisplayName("독자가 블로그 소유자의 댓글에 답하면 소유자는 답글 알림 하나만 받는다")
    fun `a reply to the owner's comment`() {
        assertThat(CommentRecipients.of(other, owner, owner)).containsExactly(CommentRecipient(owner, REPLY_TO_COMMENT))
    }

    @Test
    @DisplayName("자기 댓글에 답글을 달면 블로그 소유자만 받는다")
    fun `a reply to oneself`() {
        assertThat(CommentRecipients.of(reader, owner, reader)).containsExactly(CommentRecipient(owner, COMMENT_ON_POST))
    }

    @Test
    @DisplayName("블로그 소유자가 자기 댓글에 답글을 달면 아무도 받지 않는다")
    fun `the owner replies to themselves`() {
        assertThat(CommentRecipients.of(owner, owner, owner)).isEmpty()
    }
}
