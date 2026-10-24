package com.contentria.app.worker

import org.flywaydb.core.Flyway
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

/**
 * One PostgreSQL, one MinIO and one ffmpeg for every worker test in this JVM. The worker's scratch
 * directory is bound into the ffmpeg container at the same path, so the paths the transcoder hands
 * to ffmpeg mean the same file on both sides.
 */
object WorkerTestEnvironment {

    const val BUCKET = "contentria-media"

    val workDir: Path = Files.createDirectories(Path.of("build", "transcode-work").toAbsolutePath())

    private val postgres = PostgreSQLContainer("postgres:17").apply {
        start()
        Flyway.configure().dataSource(jdbcUrl, username, password)
            .schemas("media").defaultSchema("media").locations("classpath:db/migration/media").load().migrate()
    }

    /** MinIO no longer publishes images on Docker Hub; Chainguard's build offers only `latest` free of charge. */
    private val minio = MinIOContainer(
        DockerImageName.parse("cgr.dev/chainguard/minio:latest").asCompatibleSubstituteFor("minio/minio"),
    ).apply { start() }

    /**
     * The image's entrypoint is ffmpeg itself; it is kept idle here and each run is an exec. It runs
     * as the owner of [workDir], as the worker image runs ffmpeg as its own user: as root on Linux it
     * would leave root-owned outputs the transcoder cannot delete.
     */
    val ffmpeg: GenericContainer<*> = GenericContainer(DockerImageName.parse("jrottenberg/ffmpeg:7.1-ubuntu2404"))
        .withCreateContainerCmdModifier {
            it.withEntrypoint("sleep").withCmd("infinity")
                .withUser("${Files.getAttribute(workDir, "unix:uid")}:${Files.getAttribute(workDir, "unix:gid")}")
        }
        .withFileSystemBind(workDir.toString(), workDir.toString(), BindMode.READ_WRITE)
        .apply { start() }

    val storage: S3Client = S3Client.builder()
        .endpointOverride(URI.create(minio.s3URL))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.userName, minio.password)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()
        .apply { createBucket { it.bucket(BUCKET) } }

    private val fixtures = mutableMapOf<String, Path>()

    /**
     * A test video made by ffmpeg's own sources: [height] lines tall, 16:9, [seconds] long, with a
     * tone when [withAudio]. Made once per shape.
     */
    fun sample(height: Int, seconds: Int, withAudio: Boolean): Path = synchronized(fixtures) {
        val name = "sample-$height-$seconds-$withAudio.mp4"
        fixtures.getOrPut(name) {
            val target = Files.createDirectories(workDir.resolve("fixtures")).resolve(name)
            val width = height * 16 / 9 / 2 * 2
            val command = mutableListOf("ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=size=${width}x$height:rate=24:duration=$seconds")
            if (withAudio) command += listOf("-f", "lavfi", "-i", "sine=frequency=440:duration=$seconds", "-c:a", "aac")
            command += listOf("-c:v", "libx264", "-pix_fmt", "yuv420p", target.toString())
            val result = ffmpeg.execInContainer(*command.toTypedArray())
            check(result.exitCode == 0) { result.stderr.takeLast(500) }
            target
        }
    }

    fun execute(sql: String, vararg args: Any?) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
                statement.executeUpdate()
            }
        }
    }

    /** The first row of [sql] as column name to value, or null. */
    fun row(sql: String, vararg args: Any?): Map<String, Any?>? =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
                statement.executeQuery().use { result ->
                    if (!result.next()) null else (1..result.metaData.columnCount).associate { result.metaData.getColumnLabel(it) to result.getObject(it) }
                }
            }
        }

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("spring.datasource.url") { postgres.jdbcUrl }
        registry.add("spring.datasource.username") { postgres.username }
        registry.add("spring.datasource.password") { postgres.password }
        registry.add("contentria.media.public-base-url") { "${minio.s3URL}/$BUCKET" }
        registry.add("contentria.media.storage.endpoint") { minio.s3URL }
        registry.add("contentria.media.storage.bucket") { BUCKET }
        registry.add("contentria.media.storage.region") { "us-east-1" }
        registry.add("contentria.media.storage.access-key-id") { minio.userName }
        registry.add("contentria.media.storage.secret-access-key") { minio.password }
        registry.add("contentria.media.worker.transcode.work-dir") { workDir.resolve("jobs").toString() }
    }
}
