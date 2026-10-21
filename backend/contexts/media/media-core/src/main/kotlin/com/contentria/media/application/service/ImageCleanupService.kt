package com.contentria.media.application.service

import com.contentria.media.application.provided.CleanUpImagesUseCase
import com.contentria.media.application.provided.CleanupReport
import com.contentria.media.application.required.MediaRepository
import com.contentria.media.application.required.ObjectStorage
import com.contentria.media.domain.image.Media
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/**
 * docs/contexts/media/01-rules.md, 흐름 6. Each image goes in its own transaction that holds its row:
 * whether it is still expired is decided again under the lock, the file goes, then the record.
 * Should the file fail, the record stays for the next run; a record is never gone while its file is not.
 *
 * Unlike the request paths, the storage is called inside the transaction here: the lock has to
 * span the file's deletion, and a batch has no reader waiting on it.
 */
@Service
class ImageCleanupService(
    private val media: MediaRepository,
    private val storage: ObjectStorage,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : CleanUpImagesUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun cleanUp(): CleanupReport {
        val now = clock.instant()
        val candidates = media.findExpiredUploadIds(now.minus(Media.UPLOAD_EXPIRY)) +
            media.findUnusedIds(now.minus(Media.UNUSED_EXPIRY))
        var removed = 0
        var failed = 0
        for (id in candidates.distinct()) {
            try {
                if (remove(id)) removed++
            } catch (e: RuntimeException) {
                failed++
                log.warn("Image {} not cleaned up; its record stays for the next run", id, e)
            }
        }
        return CleanupReport(removed, failed)
    }

    private fun remove(id: UUID): Boolean = transactions.execute {
        val image = media.findLockedById(id)
        // Attached, completed or deleted since it was listed: not ours to take any more.
        if (image == null || !image.isExpired(clock.instant())) return@execute false
        storage.delete(image.storedKey)
        media.delete(image)
        true
    }!!
}
