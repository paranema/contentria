package com.contentria.publishing.application.service

import com.contentria.publishing.application.provided.GetPublishedPostsQuery
import com.contentria.publishing.application.provided.PostPage
import com.contentria.publishing.application.provided.PublishedPostLookup
import com.contentria.publishing.application.provided.PublishedPostView
import com.contentria.publishing.application.provided.PublishingUseCaseException
import com.contentria.publishing.application.required.BlogRepository
import com.contentria.publishing.application.required.CategoryTreeRepository
import com.contentria.publishing.application.required.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Reading: anyone, published posts only (docs/contexts/publishing/01-rules.md, 흐름 4와 글 slug). */
@Service
class PublishedPostService(
    private val blogs: BlogRepository,
    private val trees: CategoryTreeRepository,
    private val posts: PostRepository,
) : GetPublishedPostsQuery {

    @Transactional(readOnly = true)
    override fun inBlog(blogSlug: String, categorySlug: String?, cursor: String?): PostPage {
        val blog = blogs.findBySlug(blogSlug) ?: throw PublishingUseCaseException.BlogNotFound()
        val after = PageCursor.decode(cursor)

        val rows = if (categorySlug.isNullOrBlank()) {
            posts.findPublishedInBlog(blog.id, after.at, after.id)
        } else {
            val tree = trees.findById(blog.id)
            val category = tree?.categories?.firstOrNull { it.slug == categorySlug } ?: throw PublishingUseCaseException.CategoryNotFound()
            posts.findPublishedInCategories(blog.id, after.at, after.id, tree.withChildren(category.id))
        }
        return PageCursor.page(rows) { it.publishedAt!! }
    }

    @Transactional(readOnly = true)
    override fun recent(cursor: String?): PostPage {
        val after = PageCursor.decode(cursor)
        return PageCursor.page(posts.findRecentPublished(after.at, after.id)) { it.publishedAt!! }
    }

    @Transactional(readOnly = true)
    override fun find(blogSlug: String, postSlug: String): PublishedPostLookup? {
        val blog = blogs.findBySlug(blogSlug) ?: return null
        val post = posts.findBySlugInBlog(blog.id, postSlug)?.takeIf { it.status.isPublic } ?: return null
        if (post.slug != postSlug) return PublishedPostLookup.Moved(blog.slug, post.slug)
        return PublishedPostLookup.Found(
            PublishedPostView(
                postId = post.id, blogId = blog.id, blogSlug = blog.slug, categoryId = post.categoryId, title = post.title,
                body = post.body, summary = post.summary, publishedAt = post.publishedAt!!, slug = post.slug,
            ),
        )
    }
}
