package com.contentria.publishing.application.provided

import java.time.Instant
import java.util.UUID

/**
 * What readers see: published posts only (docs/contexts/publishing/01-rules.md, 흐름 4). Drafts and
 * private posts are answered exactly like posts that do not exist.
 */
interface GetPublishedPostsQuery {

    /**
     * The blog's published posts, newest first. With [categorySlug], that category's posts, a
     * top-level category's including its children's (흐름 2, 상위의 글 목록).
     */
    fun inBlog(blogSlug: String, categorySlug: String?, page: Int): PostPage

    /** Every blog's published posts, newest first. For the home page. */
    fun recent(page: Int): PostPage

    /** The post at [postSlug], current or old (글 slug, 옛 slug). Null when no published post is there. */
    fun find(blogSlug: String, postSlug: String): PublishedPostLookup?
}

/** A page of a list, [SIZE] at a time. [page] counts from 0; an empty list has no pages. */
data class PostPage(val items: List<PostListItem>, val page: Int, val totalItems: Long, val totalPages: Int) {
    companion object {
        const val SIZE = 20
    }
}

sealed interface PublishedPostLookup {
    data class Found(val post: PublishedPostView) : PublishedPostLookup

    /** [postSlug] is an old slug. The post now lives at [currentSlug]; the caller answers with a permanent redirect. */
    data class Moved(val blogSlug: String, val currentSlug: String) : PublishedPostLookup
}

data class PublishedPostView(
    val postId: UUID,
    val blogId: UUID,
    val blogSlug: String,
    val categoryId: UUID?,
    val title: String,
    val body: String,
    val summary: String,
    val publishedAt: Instant,
    val slug: String,
    /** The blog's owner, as in [PostListItem.authorId]. */
    val authorId: UUID,
)
