package com.contentria.publishing.application.service

import com.contentria.publishing.application.provided.PostListItem
import com.contentria.publishing.application.provided.PostPage
import com.contentria.publishing.application.provided.PublishingUseCaseException
import com.contentria.publishing.application.required.PostRow

/**
 * Numbered pages of a list (02-implementation, 목록의 페이지). Offset rather than keyset, so that
 * a screen can jump to any page; a post published while someone pages through shifts the later
 * pages by one, which a blog's lists accept.
 */
internal object PostPages {

    /** Deep enough for any blog, and far from where page × size would overflow. */
    private const val LAST_PAGE = 100_000

    fun offsetOf(page: Int): Int {
        if (page !in 0..LAST_PAGE) throw PublishingUseCaseException.InvalidPage()
        return page * PostPage.SIZE
    }

    fun of(rows: List<PostRow>, page: Int, totalItems: Long): PostPage =
        PostPage(
            items = rows.map { PostListItem(it.postId, it.blogSlug, it.categoryId, it.title, it.summary, it.status, it.publishedAt, it.slug, it.updatedAt, it.authorId) },
            page = page,
            totalItems = totalItems,
            totalPages = ((totalItems + PostPage.SIZE - 1) / PostPage.SIZE).toInt(),
        )
}
