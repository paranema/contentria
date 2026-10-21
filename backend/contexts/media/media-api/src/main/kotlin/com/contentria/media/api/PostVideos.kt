package com.contentria.media.api

import java.util.UUID

/**
 * Keeps a post's attached video attached (docs/contexts/media/01-rules.md, 흐름 V3). Publishing calls
 * it after the transaction that saved the post has committed, with the post's video or null.
 * Releasing a deleted post's video is [PostImageAttachments.releasePostImages]'s, which lets go of both.
 */
interface PostVideoAttachments {
    fun syncPostVideo(postId: UUID, ownerId: UUID, videoId: UUID?)
}

/** 흐름 V4: the videos readers may see, for whoever puts a post's page together. Whether the post is public, the caller checks. */
interface GetPostVideosQuery {
    /** A post without an attached video is missing from the result. */
    fun videosOf(postIds: Set<UUID>): Map<UUID, PostVideo>
}

/** Addresses are given only once the video is [VideoState.COMPLETED]; until then, the state alone. */
data class PostVideo(
    val videoId: UUID,
    val state: VideoState,
    val masterUrl: String?,
    val posterUrl: String?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
)

enum class VideoState { TRANSCODING, COMPLETED, FAILED }
