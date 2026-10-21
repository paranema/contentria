package com.contentria.interaction.application

import com.contentria.interaction.api.GetSubscribersQuery
import com.contentria.interaction.domain.subscription.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Subscribers for notification, a page at a time. Its own read-only transaction. */
@Service
class SubscriberQueryService(private val subscriptions: SubscriptionRepository) : GetSubscribersQuery {

    @Transactional(readOnly = true)
    override fun subscriberIds(blogId: UUID, after: UUID?): List<UUID> =
        subscriptions.findSubscriberIds(blogId, after ?: FIRST)

    private companion object {
        /** PostgreSQL orders uuids byte by byte, so none comes before all zeros. */
        val FIRST = UUID(0L, 0L)
    }
}
