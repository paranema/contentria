package com.contentria.media.application.required

import com.contentria.media.domain.image.Media
import com.contentria.media.domain.video.Video
import org.springframework.data.repository.Repository
import java.time.Instant
import java.util.UUID

// Spring Data marker repository (docs/architecture/02-internal-architecture.md, 리포지토리).
// Only the methods declared here exist.
interface MediaRepository : Repository<Media, UUID> {
    fun save(media: Media): Media
    fun findById(id: UUID): Media?
    fun delete(media: Media)
    fun findByIdIn(ids: Collection<UUID>): List<Media>

    /** [Media]'s named queries of the same names. */
    fun sumSizePreparedSince(uploaderId: UUID, since: Instant): Long
    fun findAttachedTo(postId: UUID): List<Media>
    fun findExpiredUploadIds(createdBefore: Instant): List<UUID>
    fun findUnusedIds(unattachedBefore: Instant): List<UUID>
    fun findLockedById(id: UUID): Media?
}

interface VideoRepository : Repository<Video, UUID> {
    fun save(video: Video): Video
    fun findById(id: UUID): Video?
    fun delete(video: Video)

    /** [Video]'s named queries of the same names. */
    fun findAttachedTo(postId: UUID): List<Video>
    fun findAttachedToAny(postIds: Collection<UUID>): List<Video>
    fun findStuckIds(startedBefore: Instant): List<UUID>
    fun findUnusedIds(unattachedBefore: Instant): List<UUID>
    fun findLockedById(id: UUID): Video?

    /** The video a queue notice is about: the bucket tells the original's key. */
    fun findByRawKey(rawKey: String): Video?
}
