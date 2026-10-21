package com.contentria.media.domain.image

import com.contentria.media.domain.MediaSchema
import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * An image for post bodies (docs/contexts/media/01-rules.md). Its address is fixed when the upload
 * is prepared and never changes, so saving a post moves no file.
 *
 * The posts it is attached to are its members: whether it may be attached, and whether it may be
 * deleted, are decided from the image alone (02-implementation, 애그리거트 경계). Posts are known
 * by id only.
 */
@Entity
@Table(
    name = "media",
    schema = MediaSchema.NAME,
    uniqueConstraints = [UniqueConstraint(name = "uq_media_stored_key", columnNames = ["stored_key"])],
)
class Media private constructor(
    id: UUID,
    uploaderId: UUID,
    format: ImageFormat,
    sizeBytes: Long,
    fileName: String,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @Column(name = "uploader_id", nullable = false, updatable = false)
    val uploaderId: UUID = uploaderId

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, updatable = false, length = 10)
    val format: ImageFormat = format

    @Column(name = "size_bytes", nullable = false, updatable = false)
    val sizeBytes: Long = sizeBytes

    /** As the uploader named it. Kept for screens only; the stored key does not use it. */
    @Column(name = "file_name", nullable = false, updatable = false, length = FILE_NAME_MAX_LENGTH)
    val fileName: String = fileName

    /** Where the file lives in the bucket. Fixed for good (01-rules 전제 3). */
    @Column(name = "stored_key", nullable = false, updatable = false, length = 100)
    val storedKey: String = "$KEY_PREFIX/$id.${format.extension}"

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: MediaStatus = MediaStatus.UPLOADING
        protected set

    /** Since when a ready image has been attached to no post. Null while uploading or while attached. */
    @Column(name = "unattached_since")
    var unattachedSince: Instant? = null
        protected set

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "media_attachments", schema = MediaSchema.NAME, joinColumns = [JoinColumn(name = "media_id")])
    @Column(name = "post_id", nullable = false)
    private val postIds: MutableSet<UUID> = mutableSetOf()

    val attachedPostIds: Set<UUID> get() = postIds.toSet()

    fun isUploadedBy(accountId: UUID): Boolean = uploaderId == accountId

    /** 흐름 2의 2, 6번. Returns false when it was complete already, and changes nothing then. */
    fun complete(at: Instant): Boolean {
        if (status == MediaStatus.READY) return false
        status = MediaStatus.READY
        unattachedSince = at
        return true
    }

    /**
     * 흐름 3의 2~4번. Only a ready image, and only to a post of the member who uploaded it.
     * Returns whether the image is attached to [postId] afterwards.
     */
    fun attachTo(postId: UUID, postOwnerId: UUID): Boolean {
        if (status != MediaStatus.READY || !isUploadedBy(postOwnerId)) return false
        postIds += postId
        unattachedSince = null
        return true
    }

    /** 흐름 3의 연결 풀기, 흐름 4. When the last post lets go, the image starts counting towards cleanup. */
    fun detachFrom(postId: UUID, at: Instant) {
        if (postIds.remove(postId) && postIds.isEmpty()) unattachedSince = at
    }

    /** 흐름 5의 2번 */
    fun requireDeletable() {
        if (postIds.isNotEmpty()) throw MediaException.Attached()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Media && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {

        const val MAX_SIZE_BYTES = 10L * 1024 * 1024
        const val FILE_NAME_MAX_LENGTH = 255

        /** Every image key starts with this; the bucket keeps other things under other prefixes. */
        const val KEY_PREFIX = "images"

        /** 흐름 1의 1, 2번. The daily amount is the use case's to check. */
        fun prepare(uploaderId: UUID, contentType: String, sizeBytes: Long, fileName: String): Media {
            val format = ImageFormat.of(contentType) ?: throw MediaException.UnsupportedFormat()
            if (sizeBytes !in 1..MAX_SIZE_BYTES) throw MediaException.InvalidSize()
            val name = fileName.trim()
            if (name.length !in 1..FILE_NAME_MAX_LENGTH) throw MediaException.InvalidFileName()
            return Media(Ids.newId(), uploaderId, format, sizeBytes, name)
        }
    }
}
