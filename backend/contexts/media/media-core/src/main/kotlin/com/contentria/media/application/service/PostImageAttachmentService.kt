package com.contentria.media.application.service

import com.contentria.media.api.PostImageAttachments
import com.contentria.media.application.required.MediaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** docs/contexts/media/01-rules.md, 흐름 3, 4. Each image decides for itself whether it may be attached. */
@Service
class PostImageAttachmentService(
    private val media: MediaRepository,
    private val addresses: ImageAddresses,
    private val clock: Clock,
) : PostImageAttachments {

    @Transactional
    override fun syncPostImages(postId: UUID, ownerId: UUID, imageUrls: Collection<String>) {
        val wanted = imageUrls.mapNotNull(addresses::mediaIdOf).toSet()
        val now = clock.instant()
        if (wanted.isNotEmpty()) media.findByIdIn(wanted).forEach { it.attachTo(postId, ownerId) }
        media.findAttachedTo(postId).filter { it.id !in wanted }.forEach { it.detachFrom(postId, now) }
    }

    @Transactional
    override fun releasePostImages(postId: UUID) {
        val now = clock.instant()
        media.findAttachedTo(postId).forEach { it.detachFrom(postId, now) }
    }
}
