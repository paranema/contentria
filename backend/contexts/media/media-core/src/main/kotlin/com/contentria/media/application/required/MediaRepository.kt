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

    /** Bytes of the uploads [uploaderId] prepared since [since] and still has. [Media]'s named query. */
    fun sumSizePreparedSince(uploaderId: UUID, since: Instant): Long
}
