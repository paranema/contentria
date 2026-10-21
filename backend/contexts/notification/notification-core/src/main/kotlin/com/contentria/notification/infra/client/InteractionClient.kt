package com.contentria.notification.infra.client

import com.contentria.interaction.api.GetSubscribersQuery
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The translation layer to interaction: the only place notification references it
 * (docs/contexts/notification/02-implementation.md, 다른 컨텍스트 호출). When interaction moves
 * to its own service, only this class changes to make network calls.
 */
@Component
class InteractionClient(private val subscribers: GetSubscribersQuery) {

    /** Every subscriber of [blogId] at this moment, gathered page by page. */
    fun subscribersOf(blogId: UUID): List<UUID> {
        val all = mutableListOf<UUID>()
        var after: UUID? = null
        while (true) {
            val page = subscribers.subscriberIds(blogId, after)
            all += page
            if (page.size < GetSubscribersQuery.PAGE_SIZE) return all
            after = page.last()
        }
    }
}
