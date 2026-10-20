package com.contentria.interaction.infra.client

import com.contentria.interaction.domain.target.TargetBlog
import com.contentria.interaction.domain.target.TargetPost
import com.contentria.publishing.api.GetBlogReferencesQuery
import com.contentria.publishing.api.GetPostReferencesQuery
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The translation layer to publishing: the only place interaction references it
 * (docs/contexts/interaction/02-implementation.md, 다른 컨텍스트 호출). Publishing's types stop here.
 *
 * When publishing moves to its own service, only this class changes to make a network call. An
 * unanswered call will then read as "not there": interaction refuses to write rather than attach
 * a comment to a post it could not check.
 */
@Component
class PublishingClient(
    private val posts: GetPostReferencesQuery,
    private val blogs: GetBlogReferencesQuery,
) {

    /** The post if readers can see it; null for a draft, a private post or one publishing does not know. */
    fun publicPost(postId: UUID): TargetPost? = publicPosts(setOf(postId))[postId]

    fun publicPosts(postIds: Set<UUID>): Map<UUID, TargetPost> =
        posts.getPosts(postIds).values.filter { it.isPublic }.associate { it.postId to TargetPost(it.postId, it.blogId, it.ownerId) }

    fun blogBySlug(slug: String): TargetBlog? = blogs.getBlogBySlug(slug)?.let { TargetBlog(it.blogId, it.ownerId, it.slug) }

    fun blogs(blogIds: Set<UUID>): Map<UUID, TargetBlog> =
        if (blogIds.isEmpty()) emptyMap() else blogs.getBlogs(blogIds).mapValues { (_, it) -> TargetBlog(it.blogId, it.ownerId, it.slug) }
}
