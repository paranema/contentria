package com.contentria.publishing.domain.post

import com.contentria.publishing.domain.PublishingSchema
import com.contentria.publishing.domain.slug.Slugs
import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.NamedQuery
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A post of a blog (docs/contexts/publishing/01-rules.md, 흐름 3~5와 글 slug).
 *
 * Holds the blog id only. The author is always the blog's owner, so the owner is read from the
 * blog rather than copied here, where the two could drift apart.
 *
 * Slugs: [slug] is the current one; [PostSlug] rows record every slug the post has had, the
 * current one included, so old addresses keep redirecting. Whether a slug is free in the blog is
 * the use case's question, backed by the unique constraint on `post_slugs(blog_id, slug)`.
 */
@Entity
@Table(name = "posts", schema = PublishingSchema.NAME)
// Every slug in the blog that starts with a base, other than the post's own (current and old):
// what a new slug must not collide with. Slugs hold no LIKE wildcards, so the base is safe in a pattern.
@NamedQuery(
    name = "Post.findSlugsTakenInBlog",
    query = "select s.value from PostSlug s where s.blogId = ?1 and s.value like ?2 and s.post.id <> ?3",
)
// For other contexts (publishing-api): a ranked list's titles, without the body.
@NamedQuery(
    name = "Post.findTitles",
    query = "select p.id as postId, p.blogId as blogId, p.title as title, p.slug as slug from Post p where p.id in ?1",
)
// For other contexts (publishing-api): where each post sits, who owns it, whether readers see it.
@NamedQuery(
    name = "Post.findReferences",
    query = "select p.id as postId, p.blogId as blogId, b.ownerId as ownerId, p.status as status " +
        "from Post p join Blog b on b.id = p.blogId where p.id in ?1",
)
// The post a slug of the blog leads to, current or old.
@NamedQuery(
    name = "Post.findBySlugInBlog",
    query = "select s.post from PostSlug s where s.blogId = ?1 and s.value = ?2",
)
// Lists, newest first, 20 a page and one more to tell whether another page follows. A page starts
// after the cursor: earlier, or as early with a smaller id, so posts sharing a time are not lost.
// Columns for a list only; the body (up to 100,000 characters) is not read.
@NamedQuery(
    name = "Post.findPublishedInBlog",
    query = "select p.id as postId, b.slug as blogSlug, p.categoryId as categoryId, p.title as title, " +
        "p.summary as summary, p.status as status, p.publishedAt as publishedAt, p.slug as slug, " +
        "p.updatedAt as updatedAt from Post p join Blog b on b.id = p.blogId where p.blogId = ?1 and " +
        "p.status = com.contentria.publishing.domain.post.PostStatus.PUBLISHED and (p.publishedAt < ?2 or " +
        "(p.publishedAt = ?2 and p.id < ?3)) order by p.publishedAt desc, p.id desc limit 21",
)
@NamedQuery(
    name = "Post.findPublishedInCategories",
    query = "select p.id as postId, b.slug as blogSlug, p.categoryId as categoryId, p.title as title, " +
        "p.summary as summary, p.status as status, p.publishedAt as publishedAt, p.slug as slug, " +
        "p.updatedAt as updatedAt from Post p join Blog b on b.id = p.blogId where p.blogId = ?1 and " +
        "p.categoryId in ?4 and p.status = com.contentria.publishing.domain.post.PostStatus.PUBLISHED and " +
        "(p.publishedAt < ?2 or (p.publishedAt = ?2 and p.id < ?3)) order by p.publishedAt desc, p.id " +
        "desc limit 21",
)
@NamedQuery(
    name = "Post.findRecentPublished",
    query = "select p.id as postId, b.slug as blogSlug, p.categoryId as categoryId, p.title as title, " +
        "p.summary as summary, p.status as status, p.publishedAt as publishedAt, p.slug as slug, " +
        "p.updatedAt as updatedAt from Post p join Blog b on b.id = p.blogId where p.status = " +
        "com.contentria.publishing.domain.post.PostStatus.PUBLISHED and (p.publishedAt < ?1 or " +
        "(p.publishedAt = ?1 and p.id < ?2)) order by p.publishedAt desc, p.id desc limit 21",
)
@NamedQuery(
    name = "Post.findInBlogByUpdate",
    query = "select p.id as postId, b.slug as blogSlug, p.categoryId as categoryId, p.title as title, " +
        "p.summary as summary, p.status as status, p.publishedAt as publishedAt, p.slug as slug, " +
        "p.updatedAt as updatedAt from Post p join Blog b on b.id = p.blogId where p.blogId = ?1 and " +
        "(p.updatedAt < ?2 or (p.updatedAt = ?2 and p.id < ?3)) order by p.updatedAt desc, p.id desc " +
        "limit 21",
)
class Post private constructor(
    id: UUID,
    blogId: UUID,
    categoryId: UUID?,
    content: PostContent,
    slug: String,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @Column(name = "blog_id", nullable = false, updatable = false)
    val blogId: UUID = blogId

    /** Null is "분류 없음". Also null-safe when the category was removed under the post (02-implementation). */
    @Column(name = "category_id")
    var categoryId: UUID? = categoryId
        protected set

    /** The one video attached to the post, or none. Whether it may be shown is media's to say. */
    @Column(name = "video_id")
    var videoId: UUID? = null
        protected set

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    var title: String = validTitle(content.title)
        protected set

    @Column(name = "body", nullable = false, columnDefinition = "text")
    var body: String = validBody(content.body)
        protected set

    @Column(name = "summary", nullable = false, length = PostSummary.MAX_LENGTH)
    var summary: String = content.summary.text
        protected set

    @Column(name = "summary_written", nullable = false)
    var summaryWrittenByAuthor: Boolean = content.summary.writtenByAuthor
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PostStatus = PostStatus.DRAFT
        protected set

    /** Set by the first publication and never moved, not even by republishing. */
    @Column(name = "published_at")
    var publishedAt: Instant? = null
        protected set

    @Column(name = "slug", nullable = false, length = SLUG_MAX_LENGTH)
    var slug: String = validSlug(slug)
        protected set

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val slugHistory: MutableList<PostSlug> = mutableListOf()

    /** Slugs that redirect to [slug]. Empty for a draft: nothing was ever public under them. */
    val oldSlugs: List<String>
        get() = slugHistory.map { it.value }.filter { it != slug }

    init {
        slugHistory.add(PostSlug(this, this.slug))
    }

    /**
     * Saves what the author changed. [draftSlug] is the slug regenerated from a new title; only a
     * draft takes it, because only a draft has never been seen under its address.
     */
    fun revise(content: PostContent, categoryId: UUID?, draftSlug: String? = null) {
        if (draftSlug != null && status != PostStatus.DRAFT) throw PostException.SlugFollowsTitleOnlyInDraft()
        title = validTitle(content.title)
        body = validBody(content.body)
        summary = content.summary.text
        summaryWrittenByAuthor = content.summary.writtenByAuthor
        this.categoryId = categoryId
        draftSlug?.let(::changeSlug)
    }

    /** 01-rules, 첨부 영상: one at most; null takes it off. */
    fun chooseVideo(videoId: UUID?) {
        this.videoId = videoId
    }

    /** DRAFT → PUBLISHED. Always the first publication; the use case records PostPublished for it. */
    fun publish(now: Instant) {
        if (status != PostStatus.DRAFT) throw PostException.StatusTransitionNotAllowed(status, "be published")
        status = PostStatus.PUBLISHED
        publishedAt = now
    }

    /** PUBLISHED → PRIVATE. Never back to DRAFT: the post has been public under its address. */
    fun hide() {
        if (status != PostStatus.PUBLISHED) throw PostException.StatusTransitionNotAllowed(status, "be hidden")
        status = PostStatus.PRIVATE
    }

    /** PRIVATE → PUBLISHED. Keeps the first publication time and is not a new publication. */
    fun republish() {
        if (status != PostStatus.PRIVATE) throw PostException.StatusTransitionNotAllowed(status, "be republished")
        status = PostStatus.PUBLISHED
    }

    /**
     * Moves the post to [newSlug]. A draft simply replaces its slug. A post that has been public
     * keeps the old one as a redirect; going back to one of its own old slugs adds nothing.
     */
    fun changeSlug(newSlug: String) {
        val slug = validSlug(newSlug)
        if (slug == this.slug) return

        if (status == PostStatus.DRAFT) {
            slugHistory.removeIf { it.value == this.slug }
        }
        if (slugHistory.none { it.value == slug }) slugHistory.add(PostSlug(this, slug))
        this.slug = slug
    }

    fun hasSlug(slug: String): Boolean = slugHistory.any { it.value == slug }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Post && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {

        const val TITLE_MAX_LENGTH = 150
        const val BODY_MAX_LENGTH = 100_000
        const val SLUG_MAX_LENGTH = 100

        /** A new post starts as a draft (01-rules 흐름 3의 5번). [slug] is one the use case found free. */
        fun draft(blogId: UUID, content: PostContent, slug: String, categoryId: UUID? = null): Post =
            Post(Ids.newId(), blogId, categoryId, content, slug)

        private fun validTitle(raw: String): String {
            val title = raw.trim()
            if (title.length !in 1..TITLE_MAX_LENGTH) throw PostException.InvalidTitle()
            return title
        }

        private fun validBody(body: String): String {
            if (body.length > BODY_MAX_LENGTH) throw PostException.InvalidBody(body.length)
            return body
        }

        private fun validSlug(slug: String): String {
            if (!Slugs.isValid(slug, SLUG_MAX_LENGTH)) throw PostException.InvalidSlug(slug)
            return slug
        }
    }
}
