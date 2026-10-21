package com.contentria.app.batch

import org.flywaydb.core.Flyway
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI
import java.sql.DriverManager

/** One PostgreSQL and one MinIO for every batch job test in this JVM. */
object BatchTestEnvironment {

    const val BUCKET = "contentria-media"

    /** The schemas of the contexts whose jobs run in apps/batch, migrated as the migrator does. */
    private val postgres = PostgreSQLContainer("postgres:17").apply {
        start()
        listOf("media", "analytics").forEach { context ->
            Flyway.configure().dataSource(jdbcUrl, username, password)
                .schemas(context).defaultSchema(context).locations("classpath:db/migration/$context").load().migrate()
        }
    }

    /** MinIO no longer publishes images on Docker Hub; Chainguard's build offers only `latest` free of charge. */
    private val minio = MinIOContainer(
        DockerImageName.parse("cgr.dev/chainguard/minio:latest").asCompatibleSubstituteFor("minio/minio"),
    ).apply { start() }

    val storage: S3Client = S3Client.builder()
        .endpointOverride(URI.create(minio.s3URL))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.userName, minio.password)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()
        .apply { createBucket { it.bucket(BUCKET) } }

    fun execute(sql: String, vararg args: Any?) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
                statement.executeUpdate()
            }
        }
    }

    /** The rows of [sql], each as column name to value. */
    fun rows(sql: String, vararg args: Any?): List<Map<String, Any?>> =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add((1..result.metaData.columnCount).associate { result.metaData.getColumnLabel(it) to result.getObject(it) })
                    }
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
    }
}
