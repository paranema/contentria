package com.contentria.interaction.api

import java.util.UUID

/**
 * The numbers interaction owns, for the entry points that put a screen together (post lists and
 * details, blog layouts; docs/strategic/04-context-map.md, 조회 조합). By id batch, as if remote.
 * An id with nothing recorded counts zero.
 */
interface GetInteractionCountsQuery {
    fun likeCounts(postIds: Set<UUID>): Map<UUID, Long>

    /** Comments and replies, without the places of deleted comments (01-rules, 공개 범위). */
    fun commentCounts(postIds: Set<UUID>): Map<UUID, Long>

    fun subscriberCounts(blogIds: Set<UUID>): Map<UUID, Long>
}
