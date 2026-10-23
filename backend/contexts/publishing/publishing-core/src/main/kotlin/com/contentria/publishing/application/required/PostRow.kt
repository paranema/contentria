package com.contentria.publishing.application.required

import com.contentria.publishing.domain.post.PostStatus
import java.time.Instant
import java.util.UUID

/**
 * One line of a post list, read without the body. Filled by name from the aliases of [com.contentria.publishing.domain.post.Post]'s
 * list queries.
 */
interface PostRow {
    val postId: UUID
    val blogSlug: String
    val categoryId: UUID?
    val title: String
    val summary: String
    val status: PostStatus
    val publishedAt: Instant?
    val slug: String
    val updatedAt: Instant

    /** The blog's owner, who writes every post in it: for screens that show the author. */
    val authorId: UUID
}

/** Filled by name from the aliases of [com.contentria.publishing.domain.post.Post]'s published-titles query. */
interface PublishedTitleRow {
    val postId: UUID
    val blogSlug: String
    val title: String
    val slug: String
}
