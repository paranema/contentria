package com.contentria.interaction.application

import com.contentria.interaction.domain.like.Like
import com.contentria.interaction.domain.like.LikeRepository
import com.contentria.interaction.infra.client.MemberClient
import com.contentria.interaction.infra.client.PublishingClient
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/** docs/contexts/interaction/01-rules.md, 흐름 2. 좋아요. Repeats succeed, as with subscriptions. */
@Service
class LikeService(
    private val likes: LikeRepository,
    private val member: MemberClient,
    private val publishing: PublishingClient,
    private val transactions: TransactionTemplate,
) {

    fun like(accountId: UUID, postId: UUID) {
        if (!member.canInteract(accountId)) throw InteractionException.NotAllowed()
        val post = publishing.publicPost(postId) ?: throw InteractionException.PostNotFound()
        if (post.ownerId == accountId) throw InteractionException.OwnPost()

        try {
            transactions.executeWithoutResult {
                if (!likes.existsByAccountIdAndPostId(accountId, postId)) likes.save(Like.of(accountId, postId, post.blogId))
            }
        } catch (e: DataIntegrityViolationException) {
            // A concurrent duplicate: the like exists.
        }
    }

    /** Whatever the account's or the post's state (01-rules 전제 5): no one is asked. */
    fun unlike(accountId: UUID, postId: UUID) {
        transactions.executeWithoutResult { likes.deleteByAccountIdAndPostId(accountId, postId) }
    }

    /** Which of [postIds] the account has liked. */
    @Transactional(readOnly = true)
    fun likedBy(accountId: UUID, postIds: Set<UUID>): Set<UUID> =
        if (postIds.isEmpty()) emptySet() else likes.findPostsLikedBy(accountId, postIds).toSet()
}
