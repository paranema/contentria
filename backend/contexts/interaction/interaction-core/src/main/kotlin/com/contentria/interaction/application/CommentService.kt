package com.contentria.interaction.application

import com.contentria.interaction.api.event.CommentAdded
import com.contentria.interaction.domain.comment.Comment
import com.contentria.interaction.domain.comment.CommentRepository
import com.contentria.interaction.infra.client.MemberClient
import com.contentria.interaction.infra.client.PublishingClient
import com.contentria.interaction.infra.messaging.OutboxCommentEvents
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * docs/contexts/interaction/01-rules.md, 흐름 3과 4. Member and publishing are asked before the
 * write transaction opens; deleting asks no one (01-rules 전제 5).
 */
@Service
class CommentService(
    private val comments: CommentRepository,
    private val member: MemberClient,
    private val publishing: PublishingClient,
    private val events: OutboxCommentEvents,
    private val transactions: TransactionTemplate,
) {

    private val reads = TransactionTemplate(requireNotNull(transactions.transactionManager)).apply { isReadOnly = true }

    /** A post's comments, oldest first, each top-level one with all its replies. */
    fun comments(postId: UUID, cursor: String?): CommentPage {
        publishing.publicPost(postId) ?: throw InteractionException.PostNotFound()
        val after = CommentCursor.decode(cursor)
        return reads.execute {
            val rows = comments.findTopLevelOnPost(postId, after.at, after.id)
            val shown = rows.take(CommentPage.SIZE)
            val replies = if (shown.isEmpty()) emptyMap() else comments.findByParentIdInOrderByCreatedAtAscIdAsc(shown.map { it.id }).groupBy { it.parentId }
            val next = if (rows.size > CommentPage.SIZE) shown.last().let { CommentCursor(it.createdAt, it.id).encode() } else null
            CommentPage(shown.map { CommentView.of(it, replies[it.id].orEmpty()) }, next)
        }!!
    }

    /** 흐름 3. A reply when [parentId] is given. */
    fun write(accountId: UUID, postId: UUID, body: String, parentId: UUID?): CommentView {
        if (!member.canInteract(accountId)) throw InteractionException.NotAllowed()
        val post = publishing.publicPost(postId) ?: throw InteractionException.PostNotFound()

        return transactions.execute {
            val parent = parentId?.let { id ->
                comments.findForUpdate(id)?.takeIf { it.postId == postId } ?: throw InteractionException.InvalidParent()
            }
            val comment = comments.save(parent?.reply(accountId, body) ?: Comment.write(postId, post.blogId, accountId, body))
            events.added(CommentAdded(comment.id, postId, post.blogId, post.ownerId, accountId, parent?.commenterId, comment.createdAt))
            CommentView.of(comment)
        }!!
    }

    /** 흐름 4의 1~4번 */
    fun edit(accountId: UUID, commentId: UUID, body: String): CommentView {
        if (!member.canInteract(accountId)) throw InteractionException.NotAllowed()
        val postId = comments.findById(commentId)?.postId ?: throw InteractionException.CommentNotFound()
        publishing.publicPost(postId) ?: throw InteractionException.PostNotFound()

        return transactions.execute {
            val comment = comments.findForUpdate(commentId) ?: throw InteractionException.CommentNotFound()
            comment.edit(accountId, body)
            CommentView.of(comment)
        }!!
    }

    /**
     * 흐름 4의 5, 6번. A top-level comment with replies leaves its place; anything else goes. When
     * the last reply under a place goes, the place goes with it: two comments in one transaction,
     * a cleanup the rules ask for (02-implementation, 애그리거트 경계).
     */
    fun delete(accountId: UUID, commentId: UUID) {
        transactions.executeWithoutResult {
            val comment = comments.findForUpdate(commentId) ?: throw InteractionException.CommentNotFound()
            comment.requireChangeableBy(accountId)
            val parentId = comment.parentId
            if (parentId == null) {
                if (comments.existsByParentId(comment.id)) comment.leavePlaceholder(accountId) else comments.delete(comment)
                return@executeWithoutResult
            }
            val parent = comments.findForUpdate(parentId)
            comments.delete(comment)
            if (parent != null && parent.removed && !comments.existsByParentId(parentId)) comments.delete(parent)
        }
    }
}

data class CommentPage(val comments: List<CommentView>, val nextCursor: String?) {
    companion object {
        /** Top-level comments a page; their replies come along. */
        const val SIZE = 20
    }
}

/** A comment as readers see it. The place of a deleted one shows neither words nor author. */
data class CommentView(
    val commentId: UUID,
    val parentId: UUID?,
    val commenterId: UUID?,
    val body: String,
    val edited: Boolean,
    val removed: Boolean,
    val createdAt: Instant,
    val replies: List<CommentView>,
) {
    companion object {
        fun of(comment: Comment, replies: List<Comment> = emptyList()): CommentView = CommentView(
            commentId = comment.id,
            parentId = comment.parentId,
            commenterId = comment.commenterId.takeUnless { comment.removed },
            body = comment.body,
            edited = comment.edited,
            removed = comment.removed,
            createdAt = comment.createdAt,
            replies = replies.map { of(it) },
        )
    }
}
