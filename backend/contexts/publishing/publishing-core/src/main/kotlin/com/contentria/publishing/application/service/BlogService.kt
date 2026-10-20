package com.contentria.publishing.application.service

import com.contentria.publishing.application.provided.BlogView
import com.contentria.publishing.application.provided.ChangeBlogSettingsCommand
import com.contentria.publishing.application.provided.ChangeBlogSettingsUseCase
import com.contentria.publishing.application.provided.CreateBlogCommand
import com.contentria.publishing.application.provided.CreateBlogUseCase
import com.contentria.publishing.application.provided.GetBlogQuery
import com.contentria.publishing.application.provided.PublishingUseCaseException
import com.contentria.publishing.application.required.BlogRepository
import com.contentria.publishing.application.required.BloggerStatusPort
import com.contentria.publishing.application.required.CategoryTreeRepository
import com.contentria.publishing.application.required.PostRepository
import com.contentria.publishing.domain.blog.Blog
import com.contentria.publishing.domain.category.CategoryTree
import com.contentria.publishing.domain.post.Post
import com.contentria.publishing.domain.post.PostContent
import com.contentria.publishing.domain.slug.Slugs
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/** docs/contexts/publishing/01-rules.md, 흐름 1과 블로그 설정 */
@Service
class BlogService(
    private val blogs: BlogRepository,
    private val trees: CategoryTreeRepository,
    private val posts: PostRepository,
    private val bloggers: BloggerStatusPort,
    private val summaries: PostSummaries,
    private val transactions: TransactionTemplate,
) : CreateBlogUseCase, ChangeBlogSettingsUseCase, GetBlogQuery {

    /**
     * Blog, category tree and sample drafts in one transaction: newly created aggregates have no
     * one to contend with (02-implementation, 한 트랜잭션에 여러 애그리거트를 만드는 경우).
     */
    override fun create(command: CreateBlogCommand): BlogView {
        // 1번. Asked before the transaction opens: member may one day be a network call away.
        if (!bloggers.canPublish(command.ownerId)) throw PublishingUseCaseException.BloggerNotActive()

        return try {
            transactions.execute { createInTransaction(command) }!!
        } catch (e: DataIntegrityViolationException) {
            // 5번, when two requests race for the same address past the check below.
            throw PublishingUseCaseException.BlogSlugTaken()
        }
    }

    private fun createInTransaction(command: CreateBlogCommand): BlogView {
        // 2번
        if (blogs.countByOwnerId(command.ownerId) >= Blog.MAX_PER_OWNER) throw PublishingUseCaseException.BlogLimitReached()
        // 3, 4번
        val blog = Blog.create(command.ownerId, command.slug, command.title, command.description)
        // 5번
        if (blogs.existsBySlug(blog.slug)) throw PublishingUseCaseException.BlogSlugTaken()

        val saved = blogs.save(blog)
        val tree = trees.save(CategoryTree.createFor(saved.id).apply { replaceWith(BlogSamples.categories) })
        BlogSamples.posts.forEach { sample ->
            val content = PostContent(sample.title, sample.body, summaries.of(sample.body, written = null))
            val slug = Slugs.from(sample.title, fallback = "post", maxLength = Post.SLUG_MAX_LENGTH)
            val categoryId = tree.categories.single { it.name == sample.categoryName }.id
            posts.save(Post.draft(saved.id, content, slug, categoryId))
        }
        return saved.view()
    }

    override fun change(command: ChangeBlogSettingsCommand): BlogView {
        if (!bloggers.canPublish(command.requesterId)) throw PublishingUseCaseException.BloggerNotActive()

        return transactions.execute {
            val blog = blogs.findBySlug(command.blogSlug) ?: throw PublishingUseCaseException.BlogNotFound()
            if (!blog.isOwnedBy(command.requesterId)) throw PublishingUseCaseException.NotBlogOwner()
            blog.changeSettings(command.title, command.description)
            blog.view()
        }!!
    }

    @Transactional(readOnly = true)
    override fun getBySlug(slug: String): BlogView? = blogs.findBySlug(slug)?.view()

    @Transactional(readOnly = true)
    override fun getOwnedBy(ownerId: UUID): List<BlogView> = blogs.findByOwnerIdOrderByCreatedAtAsc(ownerId).map { it.view() }

    private fun Blog.view() = BlogView(id, ownerId, slug, title, description)
}
