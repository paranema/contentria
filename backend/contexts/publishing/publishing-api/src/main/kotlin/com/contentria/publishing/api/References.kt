package com.contentria.publishing.api

import java.util.UUID

/**
 * What a downstream context needs to know about posts before it acts on one: interaction refuses
 * comments and likes on a post readers cannot see (docs/strategic/04-context-map.md).
 *
 * Asked by id batch, as if remote. An id publishing does not know is missing from the result.
 */
interface GetPostReferencesQuery {
    fun getPosts(postIds: Set<UUID>): Map<UUID, PostReference>
}

/** [isPublic] is true only for a published post. Drafts and private posts are not public. */
data class PostReference(val postId: UUID, val blogId: UUID, val ownerId: UUID, val isPublic: Boolean)

interface GetBlogReferencesQuery {
    fun getBlogBySlug(slug: String): BlogReference?
    fun getBlogs(blogIds: Set<UUID>): Map<UUID, BlogReference>
}

data class BlogReference(val blogId: UUID, val slug: String, val ownerId: UUID)
