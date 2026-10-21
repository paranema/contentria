package com.contentria.media.application.service

import com.contentria.media.application.provided.PrepareVideoUploadCommand
import com.contentria.media.application.provided.PrepareVideoUploadUseCase
import com.contentria.media.application.provided.PreparedVideoUpload
import com.contentria.media.application.required.ObjectStorage
import com.contentria.media.application.required.VideoRepository
import com.contentria.media.domain.video.Video
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/** docs/contexts/media/01-rules.md, 흐름 V1. */
@Service
class VideoUploadService(
    private val videos: VideoRepository,
    private val storage: ObjectStorage,
    private val policy: UploadPolicy,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : PrepareVideoUploadUseCase {

    override fun prepare(command: PrepareVideoUploadCommand): PreparedVideoUpload {
        val now = clock.instant()
        val video = Video.prepare(command.uploaderId, command.contentType, command.sizeBytes, command.fileName, now)
        transactions.executeWithoutResult { videos.save(video) }
        val uploadUrl = storage.uploadUrl(video.rawKey, video.format.contentType, video.sizeBytes, policy.uploadUrlValidFor)
        return PreparedVideoUpload(video.id, uploadUrl.toString(), now.plus(policy.uploadUrlValidFor))
    }
}
