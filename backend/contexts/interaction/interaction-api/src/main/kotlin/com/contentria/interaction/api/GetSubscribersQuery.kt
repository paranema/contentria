package com.contentria.interaction.api

import java.util.UUID

/**
 * Who subscribes to a blog, for notification to decide who hears of a new post
 * (docs/contexts/notification/02-implementation.md, 다른 컨텍스트 호출). Not a list for people to
 * read: interaction shows no one who subscribed (interaction 01-rules), and this is not served over HTTP.
 */
interface GetSubscribersQuery {

    /**
     * Up to [PAGE_SIZE] subscriber ids of [blogId], in id order, after [after] (the last id of the
     * previous page; null for the first). A page shorter than [PAGE_SIZE] is the last.
     */
    fun subscriberIds(blogId: UUID, after: UUID?): List<UUID>

    companion object {
        const val PAGE_SIZE = 1000
    }
}
