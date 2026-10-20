package com.contentria.interaction.domain.like

import com.contentria.interaction.domain.CountRow
import org.springframework.data.repository.Repository
import java.util.UUID

interface LikeRepository : Repository<Like, UUID> {
    fun save(like: Like): Like
    fun existsByAccountIdAndPostId(accountId: UUID, postId: UUID): Boolean
    fun deleteByAccountIdAndPostId(accountId: UUID, postId: UUID)

    /** [Like]'s named queries of the same names. */
    fun countByPosts(postIds: Collection<UUID>): List<CountRow>
    fun findPostsLikedBy(accountId: UUID, postIds: Collection<UUID>): List<UUID>
}
