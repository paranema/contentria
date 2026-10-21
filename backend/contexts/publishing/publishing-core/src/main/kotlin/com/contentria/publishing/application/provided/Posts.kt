package com.contentria.publishing.application.provided

import com.contentria.publishing.domain.post.PostStatus
import java.time.Instant
import java.util.UUID

// docs/contexts/publishing/01-rules.md, 흐름 3~5와 글 slug. Every use case here acts for the
// blog's owner and refuses anyone else.

interface WritePostUseCase {
    /** A new draft. Its slug comes from the title. */
    fun write(command: WritePostCommand): PostView
}

/** [summary] is the author's; null or blank means "derive it from the body". */
data class WritePostCommand(
    val requesterId: UUID,
    val blogSlug: String,
    val title: String,
    val body: String,
    val summary: String?,
    val categoryId: UUID?,
    val videoId: UUID? = null,
)

interface RevisePostUseCase {
    /** A draft's slug follows a changed title; a post that has been public keeps its slug. */
    fun revise(command: RevisePostCommand): PostView
}

data class RevisePostCommand(
    val requesterId: UUID,
    val postId: UUID,
    val title: String,
    val body: String,
    val summary: String?,
    val categoryId: UUID?,
    val videoId: UUID? = null,
)

interface ChangePostSlugUseCase {
    /** [newSlug] as typed, or null to make one from the title again. */
    fun changeSlug(requesterId: UUID, postId: UUID, newSlug: String?): PostView
}

interface ChangePostStatusUseCase {
    fun publish(requesterId: UUID, postId: UUID): PostView
    fun hide(requesterId: UUID, postId: UUID): PostView
    fun republish(requesterId: UUID, postId: UUID): PostView
}

interface DeletePostUseCase {
    fun delete(requesterId: UUID, postId: UUID)
}

/** The owner's view of their own posts, drafts included. Readers use [GetPublishedPostsQuery]. */
interface GetMyPostsQuery {
    fun get(requesterId: UUID, postId: UUID): PostView

    /** Every status, most recently changed first, a [PostPage.SIZE] at a time. */
    fun list(requesterId: UUID, blogSlug: String, cursor: String?): PostPage
}

data class PostView(
    val postId: UUID,
    val blogId: UUID,
    val categoryId: UUID?,
    val title: String,
    val body: String,
    val summary: String,
    val summaryWrittenByAuthor: Boolean,
    val status: PostStatus,
    val publishedAt: Instant?,
    val slug: String,
    val oldSlugs: List<String>,
    val videoId: UUID?,
)

data class PostListItem(
    val postId: UUID,
    val blogSlug: String,
    val categoryId: UUID?,
    val title: String,
    val summary: String,
    val status: PostStatus,
    val publishedAt: Instant?,
    val slug: String,
    val updatedAt: Instant,
)
