package com.contentria.interaction.application

import com.contentria.interaction.domain.subscription.Subscription
import com.contentria.interaction.domain.subscription.SubscriptionRepository
import com.contentria.interaction.infra.client.MemberClient
import com.contentria.interaction.infra.client.PublishingClient
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * docs/contexts/interaction/01-rules.md, 흐름 1. 블로그 구독.
 *
 * Subscribing and unsubscribing both describe a state to reach, so a repeat is a success rather
 * than an error (01-rules, 같은 요청을 다시 보내도 결과가 같다). Member and publishing are asked
 * before the transaction opens, as remote calls would be.
 */
@Service
class SubscriptionService(
    private val subscriptions: SubscriptionRepository,
    private val member: MemberClient,
    private val publishing: PublishingClient,
    private val transactions: TransactionTemplate,
) {

    fun subscribe(accountId: UUID, blogSlug: String) {
        if (!member.canInteract(accountId)) throw InteractionException.NotAllowed()
        val blog = publishing.blogBySlug(blogSlug) ?: throw InteractionException.BlogNotFound()
        if (blog.ownerId == accountId) throw InteractionException.OwnBlog()

        try {
            transactions.executeWithoutResult {
                if (!subscriptions.existsBySubscriberIdAndBlogId(accountId, blog.blogId)) {
                    subscriptions.save(Subscription.of(accountId, blog.blogId))
                }
            }
        } catch (e: DataIntegrityViolationException) {
            // The same subscription arrived twice at once and the unique constraint stopped the second: it exists.
        }
    }

    /** Whatever the account's or the blog's state (01-rules 전제 5). An unknown blog has nothing to remove. */
    fun unsubscribe(accountId: UUID, blogSlug: String) {
        val blog = publishing.blogBySlug(blogSlug) ?: return
        transactions.executeWithoutResult { subscriptions.deleteBySubscriberIdAndBlogId(accountId, blog.blogId) }
    }

    /** The blogs the account follows, most recent first, with their current addresses. */
    fun subscribedBlogs(accountId: UUID): List<SubscribedBlog> {
        val mine = transactions.execute { subscriptions.findBySubscriberIdOrderByCreatedAtDesc(accountId) }!!
        val blogs = publishing.blogs(mine.map { it.blogId }.toSet())
        return mine.mapNotNull { subscription ->
            blogs[subscription.blogId]?.let { SubscribedBlog(subscription.blogId, it.slug, subscription.createdAt) }
        }
    }

    @Transactional(readOnly = true)
    fun isSubscribed(accountId: UUID, blogSlug: String): Boolean {
        val blog = publishing.blogBySlug(blogSlug) ?: return false
        return subscriptions.existsBySubscriberIdAndBlogId(accountId, blog.blogId)
    }
}

data class SubscribedBlog(val blogId: UUID, val slug: String, val subscribedAt: Instant)
