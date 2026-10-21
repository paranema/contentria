package com.contentria.media.application.provided

import java.time.Instant
import java.util.UUID

// docs/contexts/media/01-rules.md, 흐름 1, 2, 5. Every use case acts for the member who uploads.

interface PrepareImageUploadUseCase {
    fun prepare(command: PrepareImageUploadCommand): PreparedImageUpload
}

data class PrepareImageUploadCommand(
    val uploaderId: UUID,
    val contentType: String,
    val sizeBytes: Long,
    val fileName: String,
)

/** PUT the file to [uploadUrl] with the declared type before [expiresAt]; the post body uses [imageUrl]. */
data class PreparedImageUpload(
    val mediaId: UUID,
    val uploadUrl: String,
    val imageUrl: String,
    val expiresAt: Instant,
)

interface CompleteImageUploadUseCase {
    fun complete(uploaderId: UUID, mediaId: UUID): ImageView
}

interface DeleteImageUseCase {
    fun delete(uploaderId: UUID, mediaId: UUID)
}

data class ImageView(
    val mediaId: UUID,
    val imageUrl: String,
    val contentType: String,
    val sizeBytes: Long,
    val fileName: String,
)
