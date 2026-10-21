package com.contentria.media.application.service

import com.contentria.media.application.provided.CleanUpVideosUseCase
import com.contentria.media.application.provided.VideoCleanupReport
import com.contentria.media.application.required.ObjectStorage
import com.contentria.media.application.required.VideoRepository
import com.contentria.media.domain.video.Video
import com.contentria.media.domain.video.VideoStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * docs/contexts/media/01-rules.md, 흐름 V5, as [ImageCleanupService] does it for images: each video in
 * its own transaction that holds its row, decided again under the lock. The stuck ones go first, so
 * that one also unused is removed in the same run.
 *
 * The files go before the record: the original, then everything under the output prefix. Should
 * either fail, the record stays for the next run, which deletes again what is left.
 */
@Service
class VideoCleanupService(
    private val videos: VideoRepository,
    private val storage: ObjectStorage,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : CleanUpVideosUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun cleanUp(): VideoCleanupReport {
        val now = clock.instant()
        var markedFailed = 0
        var removed = 0
        var failed = 0
        for (id in videos.findStuckIds(now.minus(Video.STUCK_AFTER))) {
            try {
                if (markFailed(id)) markedFailed++
            } catch (e: RuntimeException) {
                failed++
                log.warn("Stuck video {} not marked failed; it is tried again on the next run", id, e)
            }
        }
        for (id in videos.findUnusedIds(now.minus(Video.UNUSED_EXPIRY))) {
            try {
                if (remove(id)) removed++
            } catch (e: RuntimeException) {
                failed++
                log.warn("Video {} not cleaned up; its record stays for the next run", id, e)
            }
        }
        return VideoCleanupReport(markedFailed, removed, failed)
    }

    // 1번
    private fun markFailed(id: UUID): Boolean = transactions.execute {
        val video = videos.findLockedById(id)
        if (video == null || !video.isStuck(clock.instant())) return@execute false
        video.fail("Transcoding did not finish within ${Video.STUCK_AFTER.toHours()} hours")
        true
    }!!

    // 2번
    private fun remove(id: UUID): Boolean = transactions.execute {
        val video = videos.findLockedById(id)
        val now = clock.instant()
        // Attached or deleted since it was listed: not ours to take any more.
        if (video == null || !video.isExpired(now) || isLive(video, now)) return@execute false
        storage.delete(video.rawKey)
        storage.deletePrefix(video.outputPrefix)
        videos.delete(video)
        true
    }!!

    /**
     * A worker may be writing its outputs right now; removing it would leave them behind. It is
     * marked failed once stuck, and goes on the run after.
     */
    private fun isLive(video: Video, now: Instant): Boolean = video.status == VideoStatus.PROCESSING && !video.isStuck(now)
}
