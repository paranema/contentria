package com.contentria.media.application.service

import com.contentria.media.application.provided.VideoCleanupReport
import com.contentria.media.application.required.ObjectStorage
import com.contentria.media.application.required.VideoRepository
import com.contentria.media.domain.video.TranscodedVideo
import com.contentria.media.domain.video.Video
import com.contentria.media.domain.video.VideoStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class VideoCleanupServiceTest {

    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val uploader = UUID.randomUUID()

    /** The rows, keyed by id. Finds the same way the named queries do. */
    private val rows = mutableMapOf<UUID, Video>()

    private val repository = object : VideoRepository {
        override fun save(video: Video) = video.also { rows[it.id] = it }
        override fun findById(id: UUID) = rows[id]
        override fun delete(video: Video) { rows.remove(video.id) }
        override fun findAttachedTo(postId: UUID) = rows.values.filter { postId in it.attachedPostIds }
        override fun findAttachedToAny(postIds: Collection<UUID>) = rows.values.filter { it.attachedPostIds.any(postIds::contains) }
        override fun findByRawKey(rawKey: String) = rows.values.firstOrNull { it.rawKey == rawKey }
        override fun findStuckIds(startedBefore: Instant) =
            rows.values.filter { it.status == VideoStatus.PROCESSING && it.transcodingStartedAt!! < startedBefore }.map { it.id }
        override fun findUnusedIds(unattachedBefore: Instant) =
            rows.values.filter { it.unattachedSince?.isBefore(unattachedBefore) == true }.map { it.id }
        override fun findLockedById(id: UUID) = beforeLock(id).let { rows[id] }
    }

    /** Runs as the lock is taken, to change a video between listing and removing it. */
    private var beforeLock: (UUID) -> Unit = {}

    private val files = mutableSetOf<String>()
    private val failingKeys = mutableSetOf<String>()

    private val storage = object : ObjectStorage {
        override fun uploadUrl(key: String, contentType: String, sizeBytes: Long, validFor: Duration) = URI.create("http://storage.test/$key")
        override fun read(key: String): ByteArray? = null
        override fun write(key: String, bytes: ByteArray, contentType: String) { files += key }
        override fun delete(key: String) {
            check(key !in failingKeys) { "storage refused" }
            files -= key
        }
        override fun download(key: String, target: Path) = error("not used")
        override fun upload(key: String, source: Path, contentType: String) = error("not used")
        override fun deletePrefix(prefix: String) {
            check(prefix !in failingKeys) { "storage refused" }
            files.removeIf { it.startsWith(prefix) }
        }
    }

    /** Commits and rolls back nothing; the rows above are the state. */
    private val transactions = TransactionTemplate(object : AbstractPlatformTransactionManager() {
        override fun doGetTransaction() = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    })

    private val service = VideoCleanupService(repository, storage, transactions, Clock.fixed(now, ZoneOffset.UTC))

    /**
     * A video prepared [unusedFor] ago and never attached, unless [attached]. Transcoding started
     * [transcodingFor] ago when given. Its original and two outputs are in the storage.
     */
    private fun video(unusedFor: Duration, transcodingFor: Duration? = null, attached: Boolean = false): Video {
        val video = Video.prepare(uploader, "video/mp4", 10, "a.mp4", now.minus(unusedFor))
        if (attached) video.attachTo(UUID.randomUUID(), uploader)
        transcodingFor?.let { video.startTranscoding(now.minus(it)) }
        files += listOf(video.rawKey, video.outputPrefix + "master.m3u8", video.outputPrefix + "stream_0/segment_000.m4s")
        return repository.save(video)
    }

    private fun filesOf(video: Video) = files.filter { it == video.rawKey || it.startsWith(video.outputPrefix) }

    @Test
    @DisplayName("2시간 넘게 변환 중인 영상은 실패로 바꾼다. 그 안쪽은 그대로 둔다")
    fun `stuck videos fail`() {
        val stuck = video(Duration.ofDays(1), transcodingFor = Duration.ofMinutes(121), attached = true)
        val working = video(Duration.ofDays(1), transcodingFor = Duration.ofMinutes(119), attached = true)

        val report = service.cleanUp()

        assertThat(report).isEqualTo(VideoCleanupReport(markedFailed = 1, removed = 0, failed = 0))
        assertThat(stuck.status).isEqualTo(VideoStatus.FAILED)
        assertThat(stuck.failureReason).contains("2 hours")
        assertThat(working.status).isEqualTo(VideoStatus.PROCESSING)
        assertThat(filesOf(stuck)).hasSize(3)
    }

    @Test
    @DisplayName("일주일 동안 어떤 글에도 쓰이지 않은 영상은 원본, 변환 결과, 기록을 지운다. 상태와 상관없다")
    fun `unused videos go`() {
        val pending = video(Duration.ofDays(8))
        val completed = video(Duration.ofDays(8), transcodingFor = Duration.ofDays(8)).also {
            it.complete(TranscodedVideo(it.outputPrefix + "master.m3u8", null, 1000, 640, 360))
        }
        val recent = video(Duration.ofDays(6))
        val attached = video(Duration.ofDays(8), attached = true)

        val report = service.cleanUp()

        assertThat(report).isEqualTo(VideoCleanupReport(markedFailed = 0, removed = 2, failed = 0))
        assertThat(rows.keys).containsExactlyInAnyOrder(recent.id, attached.id)
        assertThat(filesOf(pending) + filesOf(completed)).isEmpty()
        assertThat(filesOf(recent)).hasSize(3)
        assertThat(filesOf(attached)).hasSize(3)
    }

    @Test
    @DisplayName("멈춘 채 쓰이지 않은 영상은 한 번에 실패로 바꾸고 지운다. 아직 변환 중이면 지우지 않는다")
    fun `stuck and unused`() {
        val stuck = video(Duration.ofDays(8), transcodingFor = Duration.ofHours(3))
        val working = video(Duration.ofDays(8), transcodingFor = Duration.ofMinutes(10))

        val report = service.cleanUp()

        assertThat(report).isEqualTo(VideoCleanupReport(markedFailed = 1, removed = 1, failed = 0))
        assertThat(rows.keys).containsExactly(working.id)
        assertThat(filesOf(stuck)).isEmpty()
        assertThat(filesOf(working)).hasSize(3)
    }

    @Test
    @DisplayName("파일을 지우지 못하면 기록을 남겨 다음에 다시 지우고, 나머지는 계속한다")
    fun `files that will not go keep the record`() {
        val rawFails = video(Duration.ofDays(8))
        val outputsFail = video(Duration.ofDays(8))
        val other = video(Duration.ofDays(8))
        failingKeys += listOf(rawFails.rawKey, outputsFail.outputPrefix)

        val report = service.cleanUp()

        assertThat(report).isEqualTo(VideoCleanupReport(markedFailed = 0, removed = 1, failed = 2))
        assertThat(rows.keys).containsExactlyInAnyOrder(rawFails.id, outputsFail.id)
        assertThat(rows).doesNotContainKey(other.id)

        failingKeys.clear()
        assertThat(service.cleanUp().removed).isEqualTo(2)
        assertThat(rows).isEmpty()
        assertThat(files).isEmpty()
    }

    @Test
    @DisplayName("목록에 오른 뒤 잠그기 전에 글에 연결된 영상은 지우지 않는다")
    fun `decided again under the lock`() {
        val video = video(Duration.ofDays(8))
        beforeLock = { rows[it]?.attachTo(UUID.randomUUID(), uploader) }

        assertThat(service.cleanUp().removed).isZero()
        assertThat(rows.keys).containsExactly(video.id)
        assertThat(filesOf(video)).hasSize(3)
    }
}
