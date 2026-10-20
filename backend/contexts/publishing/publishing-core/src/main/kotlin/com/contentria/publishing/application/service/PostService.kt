package com.contentria.publishing.application.service

import com.contentria.publishing.application.provided.ChangePostSlugUseCase
import com.contentria.publishing.application.provided.ChangePostStatusUseCase
import com.contentria.publishing.application.provided.DeletePostUseCase
import com.contentria.publishing.application.provided.GetMyPostsQuery
import com.contentria.publishing.application.provided.PostPage
import com.contentria.publishing.application.provided.PostView
import com.contentria.publishing.application.provided.PublishingUseCaseException
import com.contentria.publishing.application.provided.RevisePostCommand
import com.contentria.publishing.application.provided.RevisePostUseCase
import com.contentria.publishing.application.provided.WritePostCommand
import com.contentria.publishing.application.provided.WritePostUseCase
import com.contentria.publishing.application.required.BlogRepository
import com.contentria.publishing.application.required.BloggerStatusPort
import com.contentria.publishing.application.required.CategoryTreeRepository
import com.contentria.publishing.application.required.PostRepository
import com.contentria.publishing.domain.blog.Blog
import com.contentria.publishing.domain.post.Post
import com.contentria.publishing.domain.post.PostContent
import com.contentria.publishing.domain.post.PostStatus
import com.contentria.publishing.domain.slug.Slugs
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/** docs/contexts/publishing/01-rules.md, 흐름 3~5와 글 slug */
@Service
class PostService(
    private val blogs: BlogRepository,
    private val trees: CategoryTreeRepository,
    private val posts: PostRepository,
    private val bloggers: BloggerStatusPort,
    private val summaries: PostSummaries,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : WritePostUseCase, RevisePostUseCase, ChangePostSlugUseCase, ChangePostStatusUseCase, DeletePostUseCase, GetMyPostsQuery {

    override fun write(command: WritePostCommand): PostView = asBlogger(command.requesterId) {
        // 흐름 3의 2번
        val blog = blogs.findBySlug(command.blogSlug) ?: throw PublishingUseCaseException.BlogNotFound()
        if (!blog.isOwnedBy(command.requesterId)) throw PublishingUseCaseException.NotBlogOwner()
        // 3번
        requireCategoryOf(blog, command.categoryId)
        // 4번, then the slug the title gives
        val content = PostContent(command.title, command.body, summaries.of(command.body, command.summary))
        val draft = Post.draft(blog.id, content, slug = placeholderSlug(), categoryId = command.categoryId)
        draft.changeSlug(freeSlugFromTitle(draft))
        posts.save(draft).view()
    }

    override fun revise(command: RevisePostCommand): PostView = asBlogger(command.requesterId) {
        val (post, blog) = ownedPost(command.requesterId, command.postId)
        requireCategoryOf(blog, command.categoryId)

        val content = PostContent(command.title, command.body, summaries.of(command.body, command.summary))
        val titleChanged = content.title.trim() != post.title
        post.revise(content, command.categoryId)
        // A draft has never been public under its slug, so its slug follows the title (글 slug, 발행 전).
        if (post.status == PostStatus.DRAFT && titleChanged) post.changeSlug(freeSlugFromTitle(post))
        post.view()
    }

    override fun changeSlug(requesterId: UUID, postId: UUID, newSlug: String?): PostView = asBlogger(requesterId) {
        val (post, _) = ownedPost(requesterId, postId)
        val slug = if (newSlug.isNullOrBlank()) {
            freeSlugFromTitle(post)
        } else {
            newSlug.trim().also { typed ->
                if (posts.findSlugsTakenInBlog(post.blogId, typed, post.id).contains(typed)) {
                    throw PublishingUseCaseException.PostSlugTaken()
                }
            }
        }
        post.changeSlug(slug)
        post.view()
    }

    override fun publish(requesterId: UUID, postId: UUID): PostView = changeStatus(requesterId, postId) { it.publish(clock.instant()) }

    override fun hide(requesterId: UUID, postId: UUID): PostView = changeStatus(requesterId, postId, Post::hide)

    override fun republish(requesterId: UUID, postId: UUID): PostView = changeStatus(requesterId, postId, Post::republish)

    /** 흐름 5. The post's slugs, current and old, go with it. */
    override fun delete(requesterId: UUID, postId: UUID) {
        asBlogger(requesterId) {
            val (post, _) = ownedPost(requesterId, postId)
            posts.delete(post)
        }
    }

    override fun get(requesterId: UUID, postId: UUID): PostView =
        transactions.execute { ownedPost(requesterId, postId).first.view() }!!

    override fun list(requesterId: UUID, blogSlug: String, cursor: String?): PostPage = transactions.execute {
        val blog = blogs.findBySlug(blogSlug) ?: throw PublishingUseCaseException.BlogNotFound()
        if (!blog.isOwnedBy(requesterId)) throw PublishingUseCaseException.NotBlogOwner()
        val after = PageCursor.decode(cursor)
        PageCursor.page(posts.findInBlogByUpdate(blog.id, after.at, after.id)) { it.updatedAt }
    }!!

    private fun changeStatus(requesterId: UUID, postId: UUID, change: (Post) -> Unit): PostView = asBlogger(requesterId) {
        val (post, _) = ownedPost(requesterId, postId)
        change(post)
        post.view()
    }

    /**
     * Runs [block] for an active blogger. The account is checked before the transaction opens, as a
     * remote call would be. A slug that another request took in the meantime trips the unique
     * constraint at commit; that request fails and can be retried (02-implementation, DB 제약).
     */
    private fun <T> asBlogger(requesterId: UUID, block: () -> T): T {
        if (!bloggers.canPublish(requesterId)) throw PublishingUseCaseException.BloggerNotActive()
        return try {
            transactions.execute { block() }!!
        } catch (e: DataIntegrityViolationException) {
            throw PublishingUseCaseException.PostSlugTaken()
        }
    }

    /** The post and its blog, for the blog's owner only. Anyone else gets "no such post", whatever its status. */
    private fun ownedPost(requesterId: UUID, postId: UUID): Pair<Post, Blog> {
        val post = posts.findById(postId) ?: throw PublishingUseCaseException.PostNotFound()
        val blog = blogs.findById(post.blogId) ?: throw PublishingUseCaseException.PostNotFound()
        if (!blog.isOwnedBy(requesterId)) throw PublishingUseCaseException.PostNotFound()
        return post to blog
    }

    private fun requireCategoryOf(blog: Blog, categoryId: UUID?) {
        if (categoryId == null) return
        val tree = trees.findById(blog.id)
        if (tree == null || !tree.contains(categoryId)) throw PublishingUseCaseException.CategoryNotInBlog()
    }

    /** The title's slug, suffixed past every slug the blog's other posts have or had. */
    private fun freeSlugFromTitle(post: Post): String {
        val base = Slugs.from(post.title, fallback = "post", maxLength = Post.SLUG_MAX_LENGTH)
        val taken = posts.findSlugsTakenInBlog(post.blogId, "$base%", post.id).toSet()
        return Slugs.nextAvailable(base, taken, Post.SLUG_MAX_LENGTH)
    }

    /** A new post needs some slug to exist; the real one replaces it before anything is stored. */
    private fun placeholderSlug(): String = "draft-${UUID.randomUUID()}"

    private fun Post.view() = PostView(
        postId = id, blogId = blogId, categoryId = categoryId, title = title, body = body, summary = summary,
        summaryWrittenByAuthor = summaryWrittenByAuthor, status = status, publishedAt = publishedAt,
        slug = slug, oldSlugs = oldSlugs,
    )
}
