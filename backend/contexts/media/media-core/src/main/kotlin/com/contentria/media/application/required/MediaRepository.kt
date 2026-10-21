package com.contentria.media.application.required

import com.contentria.media.domain.image.Media
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
