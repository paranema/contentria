package com.contentria.interaction.api.event

import java.time.Instant
import java.util.UUID

/** Topic names: `<context>.<aggregate>` (docs/architecture/03-context-integration.md, 토픽). */
object InteractionTopics {
    const val COMMENT = "interaction.comment"
}

/**
 * A comment or a reply was written (docs/contexts/interaction/02-implementation.md, 이벤트).
 * Carries everyone notification may tell, the blog's owner and, for a reply, the parent's
 * author, so that it does not have to ask interaction or publishing back.
 */
data class CommentAdded(
    val commentId: UUID,
    val postId: UUID,
    val blogId: UUID,
    val blogOwnerId: UUID,
    val commenterId: UUID,
    /** The author of the comment replied to; null for a top-level comment. */
    val parentCommenterId: UUID?,
    val addedAt: Instant,
) {
    companion object {
        const val TYPE = "CommentAdded"
        const val VERSION = 1
    }
}
