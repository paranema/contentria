package com.contentria.publishing.application.service

import com.contentria.publishing.api.BlogReference
import com.contentria.publishing.api.GetBlogReferencesQuery
import com.contentria.publishing.api.GetPostReferencesQuery
import com.contentria.publishing.api.PostReference
import com.contentria.publishing.application.required.BlogRepository
import com.contentria.publishing.application.required.PostRepository
import com.contentria.publishing.domain.blog.Blog
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Posts and blogs for other contexts. Runs in its own read-only transaction rather than the
 * caller's, which is how it will behave once it sits behind a network call.
 */
@Service
class ReferenceQueryService(
    private val blogs: BlogRepository,
    private val posts: PostRepository,
) : GetPostReferencesQuery, GetBlogReferencesQuery {

    @Transactional(readOnly = true)
    override fun getPosts(postIds: Set<UUID>): Map<UUID, PostReference> {
        if (postIds.isEmpty()) return emptyMap()
        return posts.findReferences(postIds).associate { it.postId to PostReference(it.postId, it.blogId, it.ownerId, it.status.isPublic) }
    }

    @Transactional(readOnly = true)
    override fun getBlogBySlug(slug: String): BlogReference? = blogs.findBySlug(slug)?.reference()

    @Transactional(readOnly = true)
    override fun getBlogs(blogIds: Set<UUID>): Map<UUID, BlogReference> =
        if (blogIds.isEmpty()) emptyMap() else blogs.findByIdIn(blogIds).associate { it.id to it.reference() }

    private fun Blog.reference() = BlogReference(id, slug, ownerId)
}
