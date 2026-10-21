package com.contentria.media.application.service

import com.contentria.media.application.provided.TranscodeVideoUseCase
import com.contentria.media.application.provided.TranscodingOutcome
import com.contentria.media.application.required.NotAVideo
import com.contentria.media.application.required.ObjectStorage
import com.contentria.media.application.required.TranscodeJob
import com.contentria.media.application.required.Transcoder
import com.contentria.media.application.required.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * docs/contexts/media/01-rules.md, 흐름 V2. Transcoding takes minutes and happens outside any
 * transaction; the state changes before and after are short ones. Created by the worker's
 * configuration, not scanned: only the worker has a transcoder.
 */
class VideoTranscodingService(
    private val videos: VideoRepository,
    private val transcoder: Transcoder,
    private val storage: ObjectStorage,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : TranscodeVideoUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(rawKey: String): TranscodingOutcome {
        // 1번, 2번
        val job = transactions.execute {
            val video = videos.findByRawKey(rawKey) ?: return@execute null
            if (!video.startTranscoding(clock.instant())) return@execute null
            TranscodeJob(video.id, video.rawKey, video.outputPrefix)
        } ?: return TranscodingOutcome.SKIPPED

        val result = try {
            transcoder.transcode(job)
        } catch (e: NotAVideo) {
            // 3번: no retry helps. Readers are told it failed.
            transactions.executeWithoutResult { videos.findById(job.videoId)?.fail(e.message ?: "not a video") }
            return TranscodingOutcome.FAILED
        }
        // 5번
        transactions.executeWithoutResult { videos.findById(job.videoId)?.complete(result) }
        try {
            // The original is not needed once streamable. A miss is left to the cleanup.
            storage.delete(job.rawKey)
        } catch (e: RuntimeException) {
            log.warn("Original of video {} not deleted after transcoding", job.videoId, e)
        }
        return TranscodingOutcome.COMPLETED
    }
}
