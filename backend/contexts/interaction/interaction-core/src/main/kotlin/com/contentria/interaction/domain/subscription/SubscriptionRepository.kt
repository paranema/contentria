package com.contentria.interaction.domain.subscription

import com.contentria.interaction.domain.CountRow
import org.springframework.data.repository.Repository
import java.util.UUID

// Spring Data marker repository, in the domain as the layered structure has it
// (docs/architecture/02-internal-architecture.md, 리포지토리). Only the methods declared here exist.
interface SubscriptionRepository : Repository<Subscription, UUID> {
    fun save(subscription: Subscription): Subscription
    fun existsBySubscriberIdAndBlogId(subscriberId: UUID, blogId: UUID): Boolean
    fun deleteBySubscriberIdAndBlogId(subscriberId: UUID, blogId: UUID)
    fun findBySubscriberIdOrderByCreatedAtDesc(subscriberId: UUID): List<Subscription>

    /** [Subscription]'s named query of the same name. Blogs without subscribers are absent. */
    fun countByBlogs(blogIds: Collection<UUID>): List<CountRow>
}
