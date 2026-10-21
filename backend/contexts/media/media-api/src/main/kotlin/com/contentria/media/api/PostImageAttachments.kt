package com.contentria.media.api

import java.util.UUID

/**
 * Keeps the images a post uses attached to it (docs/contexts/media/01-rules.md, 흐름 3, 4). Publishing
 * calls it after the transaction that saved or deleted the post has committed. A write, yet a
 * synchronous call: docs/strategic/04-context-map.md, 동기 호출.
 */
interface PostImageAttachments {

    /**
     * Attaches to [postId] the images among [imageUrls] that [ownerId] uploaded and completed, and
     * lets go of the post's other images. Addresses that are not media's are ignored. Calling it
     * again with the same arguments changes nothing.
     */
    fun syncPostImages(postId: UUID, ownerId: UUID, imageUrls: Collection<String>)

    /** The post is gone: its images let go of it, and the cleanup takes those no post uses. */
    fun releasePostImages(postId: UUID)
}
