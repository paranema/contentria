package com.contentria.analytics.infra.client

import com.contentria.analytics.domain.target.TargetBlog
import com.contentria.analytics.domain.target.TargetPost
import com.contentria.publishing.api.GetBlogReferencesQuery
import com.contentria.publishing.api.GetPostReferencesQuery
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The translation layer to publishing: the only place analytics references it
 * (docs/contexts/analytics/02-implementation.md, 다른 컨텍스트 호출). When publishing moves to its
 * own service, only this class changes; an unanswered call then reads as "not there" and the
 * visit is dropped.
 *
 * Named apart: interaction has a translation layer to publishing of the same class name, and
 * both live in one process.
 */
@Component("analyticsPublishingClient")
class PublishingClient(
    private val blogs: GetBlogReferencesQuery,
    private val posts: GetPostReferencesQuery,
) {

    fun blog(blogId: UUID): TargetBlog? = blogs.getBlogs(setOf(blogId))[blogId]?.let { TargetBlog(it.blogId, it.ownerId) }

    /** The post if readers can see it; null for a draft, a private post or one publishing does not know. */
    fun publicPost(postId: UUID): TargetPost? =
        posts.getPosts(setOf(postId))[postId]?.takeIf { it.isPublic }?.let { TargetPost(it.postId, it.blogId) }
}
