package com.contentria.interaction.application

import com.contentria.interaction.domain.comment.CommentRepository
import com.contentria.interaction.domain.like.LikeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 01-rules, 다른 컨텍스트의 일로 생기는 것: a deleted post takes its comments and likes along.
 * Many aggregates in one transaction, a cleanup after the post itself is gone; the inbox records
 * the event in the same transaction (02-implementation, 애그리거트 경계).
 */
@Service
class PostCleanupService(
    private val comments: CommentRepository,
    private val likes: LikeRepository,
) {

    @Transactional
    fun removeAllFor(postId: UUID) {
        comments.deleteByPostId(postId)
        likes.deleteByPostId(postId)
    }
}
