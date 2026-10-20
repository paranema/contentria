package com.contentria.interaction.domain.comment

import com.contentria.interaction.domain.CountRow
import org.springframework.data.repository.Repository
import java.time.Instant
import java.util.UUID

interface CommentRepository : Repository<Comment, UUID> {
    fun save(comment: Comment): Comment
    fun findById(id: UUID): Comment?
    fun delete(comment: Comment)
    fun existsById(id: UUID): Boolean
    fun existsByParentId(parentId: UUID): Boolean
    fun findByParentIdInOrderByCreatedAtAscIdAsc(parentIds: Collection<UUID>): List<Comment>

    /** [Comment]'s named queries of the same names. */
    fun findForUpdate(id: UUID): Comment?
    fun findTopLevelOnPost(postId: UUID, afterAt: Instant, afterId: UUID): List<Comment>
    fun countByPosts(postIds: Collection<UUID>): List<CountRow>
}
