package com.contentria.media.application.service

import com.contentria.media.api.GetPostVideosQuery
import com.contentria.media.api.PostVideo
import com.contentria.media.api.PostVideoAttachments
import com.contentria.media.api.VideoState
import com.contentria.media.application.required.VideoRepository
import com.contentria.media.domain.video.Video
import com.contentria.media.domain.video.VideoStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** docs/contexts/media/01-rules.md, 흐름 V3, V4. */
@Service
class PostVideoService(
    private val videos: VideoRepository,
    private val addresses: ImageAddresses,
    private val clock: Clock,
) : PostVideoAttachments, GetPostVideosQuery {

    /** The post's video becomes [videoId] if it is the owner's; any other video the post had lets go. */
    @Transactional
    override fun syncPostVideo(postId: UUID, ownerId: UUID, videoId: UUID?) {
        val now = clock.instant()
        val attached = videoId?.let(videos::findById)?.takeIf { it.attachTo(postId, ownerId) }
        videos.findAttachedTo(postId).filter { it.id != attached?.id }.forEach { it.detachFrom(postId, now) }
    }

    @Transactional(readOnly = true)
    override fun videosOf(postIds: Set<UUID>): Map<UUID, PostVideo> {
        if (postIds.isEmpty()) return emptyMap()
        val found = videos.findAttachedToAny(postIds)
        return postIds.mapNotNull { post -> found.firstOrNull { post in it.attachedPostIds }?.let { post to view(it) } }.toMap()
    }

    private fun view(video: Video): PostVideo = when (video.status) {
        VideoStatus.COMPLETED -> PostVideo(
            video.id, VideoState.COMPLETED,
            video.masterKey?.let(addresses::urlOfKey), video.posterKey?.let(addresses::urlOfKey),
            video.durationMs, video.width, video.height,
        )
        VideoStatus.FAILED -> PostVideo(video.id, VideoState.FAILED, null, null, null, null, null)
        VideoStatus.PENDING, VideoStatus.PROCESSING -> PostVideo(video.id, VideoState.TRANSCODING, null, null, null, null, null)
    }
}
