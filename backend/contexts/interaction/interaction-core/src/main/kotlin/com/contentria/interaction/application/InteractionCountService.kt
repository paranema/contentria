package com.contentria.interaction.application

import com.contentria.interaction.api.GetInteractionCountsQuery
import com.contentria.interaction.domain.like.LikeRepository
import com.contentria.interaction.domain.subscription.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Counts for screens and for other contexts. Its own read-only transaction. Counts are grouped
 * queries over indexed columns; a stored counter waits until they show up as slow.
 */
@Service
class InteractionCountService(
    private val likes: LikeRepository,
    private val subscriptions: SubscriptionRepository,
) : GetInteractionCountsQuery {

    @Transactional(readOnly = true)
    override fun likeCounts(postIds: Set<UUID>): Map<UUID, Long> =
        withZeros(postIds) { likes.countByPosts(it).associate { row -> row.id to row.total } }

    @Transactional(readOnly = true)
    override fun subscriberCounts(blogIds: Set<UUID>): Map<UUID, Long> =
        withZeros(blogIds) { subscriptions.countByBlogs(it).associate { row -> row.id to row.total } }

    private fun withZeros(ids: Set<UUID>, counted: (Set<UUID>) -> Map<UUID, Long>): Map<UUID, Long> {
        if (ids.isEmpty()) return emptyMap()
        val found = counted(ids)
        return ids.associateWith { found[it] ?: 0L }
    }
}
