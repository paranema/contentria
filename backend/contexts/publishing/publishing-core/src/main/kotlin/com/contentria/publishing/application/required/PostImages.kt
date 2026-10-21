package com.contentria.publishing.application.required

import java.util.UUID

/** The image addresses a Markdown body shows, in order, repeats included. */
interface MarkdownImages {
    fun urlsIn(markdown: String): List<String>
}

/**
 * Tells media which images and which video a post uses (docs/contexts/media/02-implementation.md, 다른 컨텍스트와의
 * 관계). Called after the post's transaction has committed.
 */
interface PostImages {
    fun sync(postId: UUID, ownerId: UUID, imageUrls: List<String>)

    /** The post's attached video, or null for none (01-rules, 첨부 영상). */
    fun syncVideo(postId: UUID, ownerId: UUID, videoId: UUID?)

    /** The post is gone: its images and its video let go of it. */
    fun release(postId: UUID)
}
