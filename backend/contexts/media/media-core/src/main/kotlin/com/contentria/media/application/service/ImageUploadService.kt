package com.contentria.media.application.service

import com.contentria.media.application.provided.CompleteImageUploadUseCase
import com.contentria.media.application.provided.DeleteImageUseCase
import com.contentria.media.application.provided.ImageView
import com.contentria.media.application.provided.MediaUseCaseException
import com.contentria.media.application.provided.PrepareImageUploadCommand
import com.contentria.media.application.provided.PrepareImageUploadUseCase
import com.contentria.media.application.provided.PreparedImageUpload
import com.contentria.media.application.required.ImageCleaner
import com.contentria.media.application.required.MediaRepository
import com.contentria.media.application.required.ObjectStorage
import com.contentria.media.domain.image.ImageFormat
import com.contentria.media.domain.image.Media
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * docs/contexts/media/01-rules.md, 흐름 1, 2, 5. The bucket is outside any transaction: no
 * database connection waits on the network, and the order of steps leaves at worst a record whose
 * file is missing or a file the cleanup can still find by its record.
 */
@Service
class ImageUploadService(
    private val media: MediaRepository,
    private val storage: ObjectStorage,
    private val cleaner: ImageCleaner,
    private val addresses: ImageAddresses,
    private val policy: UploadPolicy,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : PrepareImageUploadUseCase, CompleteImageUploadUseCase, DeleteImageUseCase {

    override fun prepare(command: PrepareImageUploadCommand): PreparedImageUpload {
        val image = Media.prepare(command.uploaderId, command.contentType, command.sizeBytes, command.fileName)
        transactions.executeWithoutResult {
            // Two uploads prepared at the same moment may together pass the limit; the limit is a guard, not a ledger.
            val used = media.sumSizePreparedSince(command.uploaderId, startOfToday())
            if (used + image.sizeBytes > DAILY_LIMIT_BYTES) throw MediaUseCaseException.DailyLimitReached()
            media.save(image)
        }
        val uploadUrl = storage.uploadUrl(image.storedKey, image.format.contentType, image.sizeBytes, policy.uploadUrlValidFor)
        return PreparedImageUpload(image.id, uploadUrl.toString(), addresses.urlOf(image), clock.instant().plus(policy.uploadUrlValidFor))
    }

    override fun complete(uploaderId: UUID, mediaId: UUID): ImageView {
        val image = mine(uploaderId, mediaId)
        if (image.isReady()) return view(image)

        val file = storage.read(image.storedKey) ?: throw MediaUseCaseException.UploadMissing()
        if (file.size.toLong() != image.sizeBytes || !image.format.matches(file.copyOf(minOf(file.size, ImageFormat.HEADER_BYTES)))) {
            reject(image)
        }
        if (image.format == ImageFormat.JPEG) {
            val cleaned = cleaner.jpegWithoutMetadata(file) ?: reject(image)
            storage.write(image.storedKey, cleaned, image.format.contentType)
        }

        return transactions.execute {
            val current = media.findById(mediaId) ?: throw MediaUseCaseException.ImageNotFound()
            current.complete(clock.instant())
            view(current)
        }!!
    }

    override fun delete(uploaderId: UUID, mediaId: UUID) {
        val image = mine(uploaderId, mediaId)
        image.requireDeletable()
        // The file first: should the record go first and the file stay, nothing would ever find the file again.
        storage.delete(image.storedKey)
        transactions.executeWithoutResult { media.findById(mediaId)?.let(media::delete) }
    }

    /** 흐름 2의 4번: the record and the file go, and the browser is told why. */
    private fun reject(image: Media): Nothing {
        storage.delete(image.storedKey)
        transactions.executeWithoutResult { media.findById(image.id)?.let(media::delete) }
        throw MediaUseCaseException.ContentMismatch()
    }

    private fun mine(uploaderId: UUID, mediaId: UUID): Media =
        media.findById(mediaId)?.takeIf { it.isUploadedBy(uploaderId) } ?: throw MediaUseCaseException.ImageNotFound()

    private fun view(image: Media) =
        ImageView(image.id, addresses.urlOf(image), image.format.contentType, image.sizeBytes, image.fileName)

    private fun startOfToday() = LocalDate.now(clock.withZone(SEOUL)).atStartOfDay(SEOUL).toInstant()

    companion object {
        /** 01-rules, 처음 정하는 값: a member's uploads for one day, Korean time. */
        const val DAILY_LIMIT_BYTES = 100L * 1024 * 1024

        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

/** How long a browser may take to put a file. Configured by the host. */
data class UploadPolicy(val uploadUrlValidFor: Duration)
