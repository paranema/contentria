package com.contentria.media.application.provided

import java.time.Instant
import java.util.UUID

/** docs/contexts/media/01-rules.md, 흐름 V1. The transcoding starts when the file lands; nothing else is called. */
interface PrepareVideoUploadUseCase {
    fun prepare(command: PrepareVideoUploadCommand): PreparedVideoUpload
}

data class PrepareVideoUploadCommand(
    val uploaderId: UUID,
    val contentType: String,
    val sizeBytes: Long,
    val fileName: String,
)

/** PUT the file to [uploadUrl] with the declared type before [expiresAt]; the post attaches [videoId]. */
data class PreparedVideoUpload(val videoId: UUID, val uploadUrl: String, val expiresAt: Instant)
