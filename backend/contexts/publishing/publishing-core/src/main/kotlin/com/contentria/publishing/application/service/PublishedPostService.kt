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
    override fun inBlog(blogSlug: String, categorySlug: String?, page: Int): PostPage {
        val offset = PostPages.offsetOf(page)
        val blog = blogs.findBySlug(blogSlug) ?: throw PublishingUseCaseException.BlogNotFound()

        if (categorySlug.isNullOrBlank()) {
            return PostPages.of(
                posts.findPublishedInBlog(blog.id, PostPage.SIZE, offset),
                page,
                posts.countPublishedInBlog(blog.id),
            )
        }
        val tree = trees.findById(blog.id)
        val category = tree?.categories?.firstOrNull { it.slug == categorySlug } ?: throw PublishingUseCaseException.CategoryNotFound()
        val categories = tree.withChildren(category.id)
        return PostPages.of(
            posts.findPublishedInCategories(blog.id, PostPage.SIZE, offset, categories),
            page,
            posts.countPublishedInCategories(blog.id, categories),
        )
    }

    @Transactional(readOnly = true)
    override fun recent(page: Int): PostPage =
        PostPages.of(posts.findRecentPublished(PostPage.SIZE, PostPages.offsetOf(page)), page, posts.countRecentPublished())

    @Transactional(readOnly = true)
    override fun find(blogSlug: String, postSlug: String): PublishedPostLookup? {
        val blog = blogs.findBySlug(blogSlug) ?: return null
        val post = posts.findBySlugInBlog(blog.id, postSlug)?.takeIf { it.status.isPublic } ?: return null
        if (post.slug != postSlug) return PublishedPostLookup.Moved(blog.slug, post.slug)
        return PublishedPostLookup.Found(
            PublishedPostView(
                postId = post.id, blogId = blog.id, blogSlug = blog.slug, categoryId = post.categoryId, title = post.title,
                body = post.body, summary = post.summary, publishedAt = post.publishedAt!!, slug = post.slug,
                authorId = blog.ownerId,
            ),
        )
    }
}
