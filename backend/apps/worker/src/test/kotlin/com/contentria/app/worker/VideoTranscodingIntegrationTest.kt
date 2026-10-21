package com.contentria.app.worker

import com.contentria.media.adapter.provided.queue.VideoQueuePoller
import com.contentria.media.application.provided.TranscodeVideoUseCase
import com.contentria.media.application.provided.TranscodingOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.io.path.listDirectoryEntries

/** Turning an upload into HLS (media 01-rules 흐름 V2), with ffmpeg doing the real work. */
class VideoTranscodingIntegrationTest : WorkerIntegrationTest() {

    @Autowired
    private lateinit var transcoding: TranscodeVideoUseCase

    @Autowired
    private lateinit var poller: VideoQueuePoller

    @Autowired
    private lateinit var queue: FakeVideoQueue

    @Test
    @DisplayName("720p 영상은 720p와 480p HLS와 포스터가 되고, 완료로 바뀌며 원본은 지워진다")
    fun `a 720p video`() {
        val video = upload(WorkerTestEnvironment.sample(height = 720, seconds = 2, withAudio = true))

        assertThat(transcoding.handle(rawKey(video))).isEqualTo(TranscodingOutcome.COMPLETED)

        val row = row(video)
        assertThat(row["status"]).isEqualTo("COMPLETED")
        assertThat(row["master_key"]).isEqualTo("hls/$video/master.m3u8")
        assertThat(row["poster_key"]).isEqualTo("hls/$video/poster.jpg")
        assertThat(row["width"]).isEqualTo(1280)
        assertThat(row["height"]).isEqualTo(720)
        assertThat(row["duration_ms"] as Long).isBetween(1900L, 2200L)
        val master = text("hls/$video/master.m3u8")
        assertThat(master).contains("RESOLUTION=1280x720", "RESOLUTION=854x480", "CODECS=")
        assertThat(everyFileThePlaylistsName(video)).allSatisfy { assertThat(stored(it)).describedAs(it).isTrue() }
        assertThat(contentType("hls/$video/poster.jpg")).isEqualTo("image/jpeg")
        assertThat(stored(rawKey(video))).isFalse()
        assertThat(leftInWorkDir()).isEmpty()
    }

    @Test
    @DisplayName("480p보다 작은 영상은 원본 높이 하나로 만든다. 소리가 없어도 된다")
    fun `a small silent video`() {
        val video = upload(WorkerTestEnvironment.sample(height = 360, seconds = 2, withAudio = false))

        assertThat(transcoding.handle(rawKey(video))).isEqualTo(TranscodingOutcome.COMPLETED)

        val master = text("hls/$video/master.m3u8")
        assertThat(master).contains("RESOLUTION=640x360").doesNotContain("RESOLUTION=854x480")
        assertThat(everyFileThePlaylistsName(video)).allSatisfy { assertThat(stored(it)).describedAs(it).isTrue() }
        assertThat(row(video)["height"]).isEqualTo(360)
    }

    @Test
    @DisplayName("영상이 아닌 파일과 최대 길이를 넘는 영상은 실패로 바꾸고 이유를 남긴다")
    fun `what fails`() {
        val text = upload(Files.writeString(Files.createTempFile(WorkerTestEnvironment.workDir, "not-a-video", ".mp4"), "hello"))
        val tooLong = upload(WorkerTestEnvironment.sample(height = 360, seconds = 4, withAudio = false))

        assertThat(transcoding.handle(rawKey(text))).isEqualTo(TranscodingOutcome.FAILED)
        assertThat(transcoding.handle(rawKey(tooLong))).isEqualTo(TranscodingOutcome.FAILED)

        assertThat(row(text)["status"]).isEqualTo("FAILED")
        assertThat(row(text)["failure_reason"] as String).isNotBlank()
        assertThat(row(tooLong)["status"]).isEqualTo("FAILED")
        assertThat(row(tooLong)["failure_reason"] as String).contains("Longer than")
        assertThat(stored("hls/$tooLong/master.m3u8")).isFalse()
        assertThat(leftInWorkDir()).isEmpty()
    }

    @Test
    @DisplayName("같은 알림이 다시 오거나 모르는 키면 아무것도 하지 않는다")
    fun `repeated and unknown notices`() {
        val video = upload(WorkerTestEnvironment.sample(height = 360, seconds = 2, withAudio = false))
        check(transcoding.handle(rawKey(video)) == TranscodingOutcome.COMPLETED)

        assertThat(transcoding.handle(rawKey(video))).isEqualTo(TranscodingOutcome.SKIPPED)
        assertThat(row(video)["status"]).isEqualTo("COMPLETED")
        assertThat(transcoding.handle("raw/${UUID.randomUUID()}/original.mp4")).isEqualTo(TranscodingOutcome.SKIPPED)
    }

    @Test
    @DisplayName("처리한 알림과 상관없는 알림은 확인하고, 잠시 실패한 알림은 확인하지 않아 큐가 다시 보낸다")
    fun `polling`() {
        val video = upload(WorkerTestEnvironment.sample(height = 360, seconds = 2, withAudio = false))
        val missing = record(UUID.randomUUID())
        val handled = queue.send(rawKey(video))
        val output = queue.send("hls/$video/master.m3u8")
        val copied = queue.send(rawKey(video), action = "CopyObject")
        val transient = queue.send(rawKey(missing))

        poller.poll()

        assertThat(queue.acked).contains(handled, output, copied).doesNotContain(transient)
        assertThat(row(video)["status"]).isEqualTo("COMPLETED")
        assertThat(row(missing)["status"]).isEqualTo("PROCESSING")

        queue.failPulls = true
        try {
            poller.poll()
        } finally {
            queue.failPulls = false
        }
    }

    /** A video record waiting for its upload, as preparing an upload leaves it. */
    private fun record(id: UUID): UUID {
        val now = Timestamp.from(Instant.now())
        WorkerTestEnvironment.execute(
            "INSERT INTO media.videos (id, uploader_id, format, size_bytes, file_name, raw_key, status, created_at, updated_at) " +
                "VALUES (?, ?, 'MP4', 1024, 'clip.mp4', ?, 'PENDING', ?, ?)",
            id, UUID.randomUUID(), rawKey(id), now, now,
        )
        return id
    }

    /** A recorded video whose original has arrived in the bucket. */
    private fun upload(file: Path): UUID {
        val id = record(UUID.randomUUID())
        WorkerTestEnvironment.storage.putObject(
            { it.bucket(WorkerTestEnvironment.BUCKET).key(rawKey(id)).contentType("video/mp4") },
            RequestBody.fromFile(file),
        )
        return id
    }

    private fun rawKey(id: UUID) = "raw/$id/original.mp4"

    private fun row(id: UUID): Map<String, Any?> = WorkerTestEnvironment.row("SELECT * FROM media.videos WHERE id = ?", id)!!

    private fun text(key: String): String =
        WorkerTestEnvironment.storage.getObjectAsBytes { it.bucket(WorkerTestEnvironment.BUCKET).key(key) }.asUtf8String()

    private fun contentType(key: String): String =
        WorkerTestEnvironment.storage.headObject { it.bucket(WorkerTestEnvironment.BUCKET).key(key) }.contentType()

    private fun stored(key: String): Boolean =
        try {
            WorkerTestEnvironment.storage.headObject { it.bucket(WorkerTestEnvironment.BUCKET).key(key) }
            true
        } catch (e: NoSuchKeyException) {
            false
        }

    /** Every file a player would fetch, starting from the master playlist: playlists, init sections, segments. */
    private fun everyFileThePlaylistsName(id: UUID): List<String> {
        val base = "hls/$id/"
        val variants = uris(text(base + "master.m3u8"))
        check(variants.isNotEmpty())
        return variants.flatMap { variant ->
            val dir = variant.substringBeforeLast('/', "")
            val media = uris(text(base + variant)).map { if (dir.isEmpty()) it else "$dir/$it" }
            check(media.any { it.endsWith(".m4s") })
            listOf(variant) + media
        }.map { base + it }
    }

    private fun uris(playlist: String): List<String> =
        playlist.lines().flatMap { line ->
            when {
                line.isBlank() -> emptyList()
                line.startsWith("#") -> Regex("URI=\"([^\"]+)\"").findAll(line).map { it.groupValues[1] }.toList()
                else -> listOf(line.trim())
            }
        }

    private fun leftInWorkDir(): List<Path> =
        WorkerTestEnvironment.workDir.resolve("jobs").let { if (Files.exists(it)) it.listDirectoryEntries() else emptyList() }
}
