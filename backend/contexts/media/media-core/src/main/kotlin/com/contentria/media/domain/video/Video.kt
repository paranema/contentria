package com.contentria.media.domain.video

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
import jakarta.persistence.NamedQuery
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * A video attached to posts (docs/contexts/media/01-rules.md, 영상). Every state change is one of
 * these methods, whichever process makes it: the api prepares and attaches, the worker transcodes,
 * the batch cleans up (strategic/03-bounded-contexts.md, 미디어는 여러 배포 단위에 걸친 하나의 컨텍스트다).
 *
 * The posts it is attached to are its members, as an image's are. Unlike an image it may be
 * attached in any state: readers see it once it is complete.
 */
@Entity
@NamedQuery(name = "Video.findAttachedTo", query = "select v from Video v where ?1 member of v.postIds")
// The videos of several posts at once, for a list or a page: each video says which posts it is on.
@NamedQuery(name = "Video.findAttachedToAny", query = "select distinct v from Video v join v.postIds p where p in ?1")
@Table(
    name = "videos",
    schema = MediaSchema.NAME,
    uniqueConstraints = [UniqueConstraint(name = "uq_videos_raw_key", columnNames = ["raw_key"])],
)
class Video private constructor(
    id: UUID,
    uploaderId: UUID,
    format: VideoFormat,
    sizeBytes: Long,
    fileName: String,
    preparedAt: Instant,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @Column(name = "uploader_id", nullable = false, updatable = false)
    val uploaderId: UUID = uploaderId

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, updatable = false, length = 10)
    val format: VideoFormat = format

    @Column(name = "size_bytes", nullable = false, updatable = false)
    val sizeBytes: Long = sizeBytes

    @Column(name = "file_name", nullable = false, updatable = false, length = FILE_NAME_MAX_LENGTH)
    val fileName: String = fileName

    /** Where the browser puts the original. Its own folder, so the folder can go as one. */
    @Column(name = "raw_key", nullable = false, updatable = false, length = 100)
    val rawKey: String = "$RAW_PREFIX/$id/original.${format.extension}"

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: VideoStatus = VideoStatus.PENDING
        protected set

    /** When the current transcoding began; the cleanup fails a video stuck past [STUCK_AFTER]. */
    @Column(name = "transcoding_started_at")
    var transcodingStartedAt: Instant? = null
        protected set

    @Column(name = "master_key", length = 150)
    var masterKey: String? = null
        protected set

    @Column(name = "poster_key", length = 150)
    var posterKey: String? = null
        protected set

    @Column(name = "duration_ms")
    var durationMs: Long? = null
        protected set

    @Column(name = "width")
    var width: Int? = null
        protected set

    @Column(name = "height")
    var height: Int? = null
        protected set

    /** Why it failed, for the logs; readers see only that it did. */
    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null
        protected set

    /** Since when no post has used it. Set from the start: a video is attachable before it is complete. */
    @Column(name = "unattached_since")
    var unattachedSince: Instant? = preparedAt
        protected set

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "video_attachments", schema = MediaSchema.NAME, joinColumns = [JoinColumn(name = "video_id")])
    @Column(name = "post_id", nullable = false)
    private val postIds: MutableSet<UUID> = mutableSetOf()

    val attachedPostIds: Set<UUID> get() = postIds.toSet()

    /** Everything a transcoder writes for this video goes under here. */
    val outputPrefix: String get() = "$HLS_PREFIX/$id/"

    fun isUploadedBy(accountId: UUID): Boolean = uploaderId == accountId

    /**
     * 흐름 V2의 2, 5번. False when it is complete or failed already: a repeated notice changes
     * nothing. A video still marked as being transcoded starts again, as when a worker died midway
     * and the queue sent the notice once more.
     */
    fun startTranscoding(at: Instant): Boolean {
        if (status.isFinal) return false
        status = VideoStatus.PROCESSING
        transcodingStartedAt = at
        return true
    }

    /** 흐름 V2의 5번 */
    fun complete(result: TranscodedVideo) {
        if (status != VideoStatus.PROCESSING) throw VideoException.NotTranscoding()
        status = VideoStatus.COMPLETED
        masterKey = result.masterKey
        posterKey = result.posterKey
        durationMs = result.durationMs
        width = result.width
        height = result.height
    }

    /** 흐름 V2의 3번, 흐름 V5의 1번. A final video stays as it is. */
    fun fail(reason: String) {
        if (status.isFinal) return
        status = VideoStatus.FAILED
        failureReason = reason.take(FAILURE_REASON_MAX_LENGTH)
    }

    /** 흐름 V5의 1번 */
    fun isStuck(now: Instant): Boolean =
        status == VideoStatus.PROCESSING && transcodingStartedAt?.let { it < now.minus(STUCK_AFTER) } == true

    /** 흐름 V3: the uploader's posts only, in any state. Returns whether it is attached afterwards. */
    fun attachTo(postId: UUID, postOwnerId: UUID): Boolean {
        if (!isUploadedBy(postOwnerId)) return false
        postIds += postId
        unattachedSince = null
        return true
    }

    fun detachFrom(postId: UUID, at: Instant) {
        if (postIds.remove(postId) && postIds.isEmpty()) unattachedSince = at
    }

    /** 흐름 V5의 2번: no post has used it for a week, whatever its state. */
    fun isExpired(now: Instant): Boolean = unattachedSince?.let { it < now.minus(UNUSED_EXPIRY) } ?: false

    override fun equals(other: Any?): Boolean =
        this === other || (other is Video && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {

        const val MAX_SIZE_BYTES = 500L * 1024 * 1024
        const val FILE_NAME_MAX_LENGTH = 255
        const val FAILURE_REASON_MAX_LENGTH = 500

        /** The bucket's notifications cover this prefix only, so outputs never come back as new uploads. */
        const val RAW_PREFIX = "raw"
        const val HLS_PREFIX = "hls"

        /** 01-rules, 영상의 처음 정하는 값 */
        val STUCK_AFTER: Duration = Duration.ofHours(2)
        val UNUSED_EXPIRY: Duration = Duration.ofDays(7)

        /** 흐름 V1의 1, 2번 */
        fun prepare(uploaderId: UUID, contentType: String, sizeBytes: Long, fileName: String, at: Instant): Video {
            val format = VideoFormat.of(contentType) ?: throw VideoException.UnsupportedFormat()
            if (sizeBytes !in 1..MAX_SIZE_BYTES) throw VideoException.InvalidSize()
            val name = fileName.trim()
            if (name.length !in 1..FILE_NAME_MAX_LENGTH) throw VideoException.InvalidFileName()
            return Video(Ids.newId(), uploaderId, format, sizeBytes, name, at)
        }
    }
}

/** What a transcoder made of a video. Keys are under the video's [Video.outputPrefix]. */
data class TranscodedVideo(
    val masterKey: String,
    val posterKey: String?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
)
