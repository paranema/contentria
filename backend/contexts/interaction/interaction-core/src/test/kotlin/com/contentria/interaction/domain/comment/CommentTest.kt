package com.contentria.interaction.domain.comment

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class CommentTest {

    private val author = UUID.randomUUID()
    private val reader = UUID.randomUUID()

    private fun comment(body: String = "좋은 글이네요") = Comment.write(UUID.randomUUID(), UUID.randomUUID(), author, body)

    @Test
    @DisplayName("본문은 앞뒤 공백을 뺀 뒤 1~1000자이고, 안의 줄바꿈은 남긴다")
    fun `body`() {
        assertThat(comment("  첫 줄\n둘째 줄  ").body).isEqualTo("첫 줄\n둘째 줄")
        assertThatThrownBy { comment("   ") }.isInstanceOf(CommentException.InvalidBody::class.java)
        assertThatThrownBy { comment("가".repeat(1001)) }.isInstanceOf(CommentException.InvalidBody::class.java)
    }

    @Test
    @DisplayName("답글은 같은 글과 블로그에 달리고 부모를 가리킨다")
    fun `a reply sits on its parent's post`() {
        val parent = comment()

        val reply = parent.reply(reader, "감사합니다")

        assertThat(reply.parentId).isEqualTo(parent.id)
        assertThat(reply.postId).isEqualTo(parent.postId)
        assertThat(reply.blogId).isEqualTo(parent.blogId)
        assertThat(reply.commenterId).isEqualTo(reader)
        assertThat(reply.isReply).isTrue()
        assertThat(parent.isReply).isFalse()
    }

    @Test
    @DisplayName("답글에는 답글을 달 수 없다. 답글은 한 단계다")
    fun `no reply to a reply`() {
        val reply = comment().reply(reader, "답글")

        assertThatThrownBy { reply.reply(author, "답글의 답글") }.isInstanceOf(CommentException.ReplyToReply::class.java)
    }

    @Test
    @DisplayName("작성자만 고치고, 고친 댓글은 수정됨으로 표시한다")
    fun `only the author edits`() {
        val comment = comment()

        assertThatThrownBy { comment.edit(reader, "남이 고침") }.isInstanceOf(CommentException.NotAuthor::class.java)
        assertThat(comment.edited).isFalse()

        comment.edit(author, " 고친 댓글 ")
        assertThat(comment.body).isEqualTo("고친 댓글")
        assertThat(comment.edited).isTrue()
    }

    @Test
    @DisplayName("지운 자리는 본문이 없고, 고치거나 답글을 달거나 다시 지울 수 없다")
    fun `a placeholder is final`() {
        val comment = comment()
        assertThatThrownBy { comment.leavePlaceholder(reader) }.isInstanceOf(CommentException.NotAuthor::class.java)

        comment.leavePlaceholder(author)

        assertThat(comment.removed).isTrue()
        assertThat(comment.body).isEmpty()
        assertThatThrownBy { comment.edit(author, "되살리기") }.isInstanceOf(CommentException.Removed::class.java)
        assertThatThrownBy { comment.reply(reader, "답글") }.isInstanceOf(CommentException.Removed::class.java)
        assertThatThrownBy { comment.leavePlaceholder(author) }.isInstanceOf(CommentException.Removed::class.java)
    }

    @Test
    @DisplayName("지우기 전에 작성자인지 확인한다")
    fun `deleting needs the author`() {
        val comment = comment()

        assertThatThrownBy { comment.requireChangeableBy(reader) }.isInstanceOf(CommentException.NotAuthor::class.java)
        comment.requireChangeableBy(author)
    }
}
