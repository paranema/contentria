package com.contentria.app.batch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The image cleanup job through a real PostgreSQL and S3 API (media 01-rules 흐름 6). */
class MediaCleanupJobIntegrationTest : BatchIntegrationTest() {

    @Autowired
    private lateinit var mediaImageCleanupJob: Job

    @Test
    @DisplayName("하루 지난 업로드와 일주일 동안 쓰이지 않은 이미지는 파일과 기록을 지우고, 나머지는 남긴다")
    fun `the job removes what expired`() {
        val now = Instant.now()
        val oldUpload = image(created = now.minus(Duration.ofHours(25)))
        val freshUpload = image(created = now.minus(Duration.ofHours(23)))
        val unused = image(created = now.minus(Duration.ofDays(10)), unattachedSince = now.minus(Duration.ofDays(8)))
        val recentlyUnused = image(created = now.minus(Duration.ofDays(10)), unattachedSince = now.minus(Duration.ofDays(6)))
        val attached = image(created = now.minus(Duration.ofDays(10)), ready = true)
        BatchTestEnvironment.execute("INSERT INTO media.media_attachments (media_id, post_id) VALUES (?, ?)", attached, UUID.randomUUID())

        assertThat(run(mediaImageCleanupJob)).isEqualTo(BatchStatus.COMPLETED)
        assertThat(listOf(oldUpload, unused).filter { exists(it) || stored(it) }).isEmpty()
        assertThat(listOf(freshUpload, recentlyUnused, attached).filter { exists(it) && stored(it) }).hasSize(3)

        assertThat(run(mediaImageCleanupJob)).isEqualTo(BatchStatus.COMPLETED)
        assertThat(listOf(freshUpload, recentlyUnused, attached).filter { exists(it) && stored(it) }).hasSize(3)
    }

    /** An image record and its file: ready when [unattachedSince] is given or [ready] says so, uploading otherwise. */
    private fun image(created: Instant, unattachedSince: Instant? = null, ready: Boolean = unattachedSince != null): UUID {
        val id = UUID.randomUUID()
        BatchTestEnvironment.execute(
            "INSERT INTO media.media (id, uploader_id, format, size_bytes, file_name, stored_key, status, unattached_since, created_at, updated_at) " +
                "VALUES (?, ?, 'PNG', 4, 'a.png', ?, ?, ?, ?, ?)",
            id, UUID.randomUUID(), key(id), if (ready) "READY" else "UPLOADING",
            unattachedSince?.let(Timestamp::from), Timestamp.from(created), Timestamp.from(created),
        )
        BatchTestEnvironment.storage.putObject(
            { it.bucket(BatchTestEnvironment.BUCKET).key(key(id)).contentType("image/png") },
            RequestBody.fromBytes(byteArrayOf(1, 2, 3, 4)),
        )
        return id
    }

    private fun key(id: UUID) = "images/$id.png"

    private fun exists(id: UUID): Boolean = BatchTestEnvironment.rows("SELECT 1 AS found FROM media.media WHERE id = ?", id).isNotEmpty()

    private fun stored(id: UUID): Boolean =
        try {
            BatchTestEnvironment.storage.headObject { it.bucket(BatchTestEnvironment.BUCKET).key(key(id)) }
            true
        } catch (e: NoSuchKeyException) {
            false
        }
}
