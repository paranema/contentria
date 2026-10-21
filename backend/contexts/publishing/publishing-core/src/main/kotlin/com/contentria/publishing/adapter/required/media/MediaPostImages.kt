package com.contentria.publishing.adapter.required.media

import com.contentria.media.api.PostImageAttachments
import com.contentria.publishing.application.required.PostImages
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The translation layer to media: the only place publishing references it. When media moves to
 * its own service, only this class changes to make a network call.
 */
@Component
class MediaPostImages(private val attachments: PostImageAttachments) : PostImages {

    override fun sync(postId: UUID, ownerId: UUID, imageUrls: List<String>) =
        attachments.syncPostImages(postId, ownerId, imageUrls)

    override fun release(postId: UUID) = attachments.releasePostImages(postId)
}
