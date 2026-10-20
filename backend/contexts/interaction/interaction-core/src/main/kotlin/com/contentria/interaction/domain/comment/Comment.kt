package com.contentria.interaction.domain.comment

import com.contentria.interaction.domain.InteractionSchema
import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * A comment on a post, or a reply to one (docs/contexts/interaction/01-rules.md, 흐름 3과 4).
 *
 * Each comment is its own aggregate, a reply included: its parent is referenced by id. Whether a
 * comment has replies is therefore a question for the use case, which decides between deleting
 * the comment and [leaving its place][leavePlaceholder] (02-implementation, 애그리거트 경계).
 */
@Entity
@Table(name = "comments", schema = InteractionSchema.NAME)
class Comment private constructor(
    id: UUID,
    postId: UUID,
    blogId: UUID,
    commenterId: UUID,
    parentId: UUID?,
    body: String,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @Column(name = "post_id", nullable = false, updatable = false)
    val postId: UUID = postId

    @Column(name = "blog_id", nullable = false, updatable = false)
    val blogId: UUID = blogId

    /** The member who wrote it. Not the post's author, who is called the blog's owner here. */
    @Column(name = "commenter_id", nullable = false, updatable = false)
    val commenterId: UUID = commenterId

    /** The comment this one replies to; null for a top-level comment. */
    @Column(name = "parent_id", updatable = false)
    val parentId: UUID? = parentId

    /** Empty once only the place of a deleted comment remains. */
    @Column(name = "body", nullable = false, length = BODY_MAX_LENGTH)
    var body: String = validBody(body)
        protected set

    /** 01-rules 흐름 4의 4번: 고친 댓글에는 "수정됨"을 표시한다. */
    @Column(name = "edited", nullable = false)
    var edited: Boolean = false
        protected set

    /** 01-rules 흐름 4의 5번: the comment was deleted while replies hung from it. */
    @Column(name = "removed", nullable = false)
    var removed: Boolean = false
        protected set

    val isReply: Boolean get() = parentId != null

    /** 흐름 3의 4, 5번. The reply sits on the same post and blog as its parent. */
    fun reply(commenterId: UUID, body: String): Comment {
        if (isReply) throw CommentException.ReplyToReply()
        if (removed) throw CommentException.Removed()
        return Comment(Ids.newId(), postId, blogId, commenterId, parentId = id, body = body)
    }

    /** 흐름 4의 1, 2, 4번 */
    fun edit(editorId: UUID, newBody: String) {
        requireChangeableBy(editorId)
        body = validBody(newBody)
        edited = true
    }

    /**
     * 흐름 4의 5번. Clears the words and keeps the place, so the replies under it still read as
     * replies. The use case calls this only for a top-level comment that has replies.
     */
    fun leavePlaceholder(requesterId: UUID) {
        requireChangeableBy(requesterId)
        body = ""
        removed = true
    }

    /** 흐름 4의 1, 2번: only the author changes a comment, and a deleted one is not changed again. */
    fun requireChangeableBy(accountId: UUID) {
        if (commenterId != accountId) throw CommentException.NotAuthor()
        if (removed) throw CommentException.Removed()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Comment && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {

        const val BODY_MAX_LENGTH = 1000

        /** A top-level comment. Replies come from [reply]. */
        fun write(postId: UUID, blogId: UUID, commenterId: UUID, body: String): Comment =
            Comment(Ids.newId(), postId, blogId, commenterId, parentId = null, body = body)

        /** Surrounding whitespace goes; line breaks inside stay (01-rules, 처음 정하는 값). */
        private fun validBody(raw: String): String {
            val body = raw.trim()
            if (body.length !in 1..BODY_MAX_LENGTH) throw CommentException.InvalidBody()
            return body
        }
    }
}
