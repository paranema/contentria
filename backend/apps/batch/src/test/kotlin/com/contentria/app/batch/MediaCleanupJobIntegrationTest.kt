package com.contentria.app.batch

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.net.URI
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The image cleanup job through a real PostgreSQL and S3 API (media 01-rules 흐름 6). */
@SpringBootTest(properties = ["spring.batch.job.enabled=false"])
class MediaCleanupJobIntegrationTest {

    @Autowired
    private lateinit var jobOperator: JobOperator

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
        execute("INSERT INTO media.media_attachments (media_id, post_id) VALUES (?, ?)", attached, UUID.randomUUID())

        val execution = run()

        assertThat(execution).isEqualTo(BatchStatus.COMPLETED)
        assertThat(listOf(oldUpload, unused).filter { exists(it) || stored(it) }).isEmpty()
        assertThat(listOf(freshUpload, recentlyUnused, attached).filter { exists(it) && stored(it) }).hasSize(3)

        assertThat(run()).isEqualTo(BatchStatus.COMPLETED)
        assertThat(listOf(freshUpload, recentlyUnused, attached).filter { exists(it) && stored(it) }).hasSize(3)
    }

    private fun run(): BatchStatus =
        jobOperator.start(mediaImageCleanupJob, JobParametersBuilder().addLong("run", System.nanoTime()).toJobParameters()).status

    /** An image record and its file: ready when [unattachedSince] is given or [ready] says so, uploading otherwise. */
    private fun image(created: Instant, unattachedSince: Instant? = null, ready: Boolean = unattachedSince != null): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO media.media (id, uploader_id, format, size_bytes, file_name, stored_key, status, unattached_since, created_at, updated_at) " +
                "VALUES (?, ?, 'PNG', 4, 'a.png', ?, ?, ?, ?, ?)",
            id, UUID.randomUUID(), key(id), if (ready) "READY" else "UPLOADING",
            unattachedSince?.let(Timestamp::from), Timestamp.from(created), Timestamp.from(created),
        )
        storage.putObject({ it.bucket(BUCKET).key(key(id)).contentType("image/png") }, RequestBody.fromBytes(byteArrayOf(1, 2, 3, 4)))
        return id
    }

    private fun key(id: UUID) = "images/$id.png"

    private fun exists(id: UUID): Boolean =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement("SELECT 1 FROM media.media WHERE id = ?").use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { it.next() }
            }
        }

    private fun stored(id: UUID): Boolean =
        try {
            storage.headObject { it.bucket(BUCKET).key(key(id)) }
            true
        } catch (e: NoSuchKeyException) {
            false
        }

    private fun execute(sql: String, vararg args: Any?) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
                statement.executeUpdate()
            }
        }
    }

    companion object {
        private const val BUCKET = "contentria-media"

        private val postgres = PostgreSQLContainer("postgres:17").apply {
            start()
            Flyway.configure().dataSource(jdbcUrl, username, password)
                .schemas("media").defaultSchema("media").locations("classpath:db/migration/media").load().migrate()
        }

        /** MinIO no longer publishes images on Docker Hub; Chainguard's build offers only `latest` free of charge. */
        private val minio = MinIOContainer(
            DockerImageName.parse("cgr.dev/chainguard/minio:latest").asCompatibleSubstituteFor("minio/minio"),
        ).apply { start() }

        private val storage: S3Client = S3Client.builder()
            .endpointOverride(URI.create(minio.s3URL))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.userName, minio.password)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()
            .apply { createBucket { it.bucket(BUCKET) } }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("contentria.media.public-base-url") { "${minio.s3URL}/$BUCKET" }
            registry.add("contentria.media.storage.endpoint") { minio.s3URL }
            registry.add("contentria.media.storage.bucket") { BUCKET }
            registry.add("contentria.media.storage.region") { "us-east-1" }
            registry.add("contentria.media.storage.access-key-id") { minio.userName }
            registry.add("contentria.media.storage.secret-access-key") { minio.password }
        }
    }
}
