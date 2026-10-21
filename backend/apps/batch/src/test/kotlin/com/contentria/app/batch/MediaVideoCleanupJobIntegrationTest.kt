package com.contentria.app.batch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.core.sync.RequestBody
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The video cleanup job through a real PostgreSQL and S3 API (media 01-rules 흐름 V5). */
class MediaVideoCleanupJobIntegrationTest : BatchIntegrationTest() {

    @Autowired
    private lateinit var mediaVideoCleanupJob: Job

    private val now = Instant.now()

    @Test
    @DisplayName("2시간 넘게 변환 중인 영상은 실패로 바꾸고 이유를 남긴다. 그 안쪽은 그대로 둔다")
    fun `stuck videos fail`() {
        val stuck = video(status = "PROCESSING", startedAgo = Duration.ofMinutes(125), attached = true)
        val working = video(status = "PROCESSING", startedAgo = Duration.ofMinutes(115), attached = true)

        assertThat(run(mediaVideoCleanupJob)).isEqualTo(BatchStatus.COMPLETED)

        assertThat(column(stuck, "status")).isEqualTo("FAILED")
        assertThat(column(stuck, "failure_reason") as String).isNotBlank()
        assertThat(column(working, "status")).isEqualTo("PROCESSING")
        assertThat(filesOf(stuck)).hasSize(4)
    }

    @Test
    @DisplayName("일주일 동안 쓰이지 않은 영상은 원본, 결과 폴더 전체, 기록을 지운다. 연결된 영상과 6일째인 영상은 남긴다")
    fun `unused videos go`() {
        val unused = video(status = "COMPLETED", unusedFor = Duration.ofDays(8))
        val failed = video(status = "FAILED", unusedFor = Duration.ofDays(8))
        val recent = video(status = "COMPLETED", unusedFor = Duration.ofDays(6))
        val attached = video(status = "COMPLETED", attached = true)

        assertThat(run(mediaVideoCleanupJob)).isEqualTo(BatchStatus.COMPLETED)

        assertThat(exists(unused) || exists(failed)).isFalse()
        assertThat(filesOf(unused) + filesOf(failed)).isEmpty()
        assertThat(listOf(recent, attached).filter(::exists)).hasSize(2)
        assertThat(filesOf(recent)).hasSize(4)
        assertThat(filesOf(attached)).hasSize(4)

        assertThat(run(mediaVideoCleanupJob)).isEqualTo(BatchStatus.COMPLETED)
        assertThat(listOf(recent, attached).filter(::exists)).hasSize(2)
    }

    /**
     * A video record in [status] and its files: the original and three outputs. Unattached for
     * [unusedFor], or attached to a post when [attached].
     */
    private fun video(status: String, startedAgo: Duration? = null, unusedFor: Duration = Duration.ofDays(1), attached: Boolean = false): UUID {
        val id = UUID.randomUUID()
        val completed = status == "COMPLETED"
        BatchTestEnvironment.execute(
            "INSERT INTO media.videos (id, uploader_id, format, size_bytes, file_name, raw_key, status, transcoding_started_at, " +
                "master_key, duration_ms, width, height, failure_reason, unattached_since, created_at, updated_at) " +
                "VALUES (?, ?, 'MP4', 1024, 'clip.mp4', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, UUID.randomUUID(), rawKey(id), status,
            Timestamp.from(now.minus(startedAgo ?: Duration.ofDays(9))),
            if (completed) "hls/$id/master.m3u8" else null, if (completed) 1000L else null, if (completed) 640 else null, if (completed) 360 else null,
            if (status == "FAILED") "not a video" else null,
            if (attached) null else Timestamp.from(now.minus(unusedFor)),
            Timestamp.from(now.minus(Duration.ofDays(10))), Timestamp.from(now.minus(Duration.ofDays(10))),
        )
        if (attached) BatchTestEnvironment.execute("INSERT INTO media.video_attachments (video_id, post_id) VALUES (?, ?)", id, UUID.randomUUID())
        listOf(rawKey(id), "hls/$id/master.m3u8", "hls/$id/stream_0/playlist.m3u8", "hls/$id/stream_0/segment_000.m4s").forEach { key ->
            BatchTestEnvironment.storage.putObject({ it.bucket(BatchTestEnvironment.BUCKET).key(key) }, RequestBody.fromBytes(byteArrayOf(1)))
        }
        return id
    }

    private fun rawKey(id: UUID) = "raw/$id/original.mp4"

    private fun exists(id: UUID): Boolean = BatchTestEnvironment.rows("SELECT 1 AS found FROM media.videos WHERE id = ?", id).isNotEmpty()

    private fun column(id: UUID, name: String): Any? = BatchTestEnvironment.rows("SELECT $name FROM media.videos WHERE id = ?", id).single()[name]

    private fun filesOf(id: UUID): List<String> =
        listOf("raw/$id/", "hls/$id/").flatMap { prefix ->
            BatchTestEnvironment.storage.listObjectsV2 { it.bucket(BatchTestEnvironment.BUCKET).prefix(prefix) }.contents().map { it.key() }
        }
}
