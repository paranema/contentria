package com.contentria.app.api

import org.flywaydb.core.Flyway
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.MinIOContainer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** One PostgreSQL, one Kafka, one MinIO and one fake Google for every API integration test in this JVM. */
object ApiTestEnvironment {

    const val LOGIN_SUCCESS_URL = "http://frontend.test/login/callback"
    const val LOGIN_FAILURE_URL = "http://frontend.test/login"
    const val LINK_RESULT_URL = "http://frontend.test/settings/account"

    /** One Flyway per context, as the migrator runs them. */
    private val postgres = PostgreSQLContainer("postgres:17").apply {
        start()
        listOf("member", "publishing", "interaction", "notification", "media").forEach { context ->
            Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .schemas(context)
                .defaultSchema(context)
                .locations("classpath:db/migration/$context")
                .load()
                .migrate()
        }
    }

    val google = FakeOidcProvider().apply { start() }

    /**
     * The images' bucket, S3 like Cloudflare R2. Anyone may read, as the public image domain allows.
     * MinIO no longer publishes images on Docker Hub; Chainguard's build offers only `latest` free of charge.
     */
    private val minio = MinIOContainer(
        DockerImageName.parse("cgr.dev/chainguard/minio:latest").asCompatibleSubstituteFor("minio/minio"),
    ).apply { start() }

    const val MEDIA_BUCKET = "contentria-media"

    /** S3 access to the test bucket, for setting it up and for looking at what an API call left in it. */
    val storage: S3Client = S3Client.builder()
        .endpointOverride(URI.create(minio.s3URL))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.userName, minio.password)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()
        .apply {
            createBucket { it.bucket(MEDIA_BUCKET) }
            putBucketPolicy {
                it.bucket(MEDIA_BUCKET).policy(
                    """{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},""" +
                        """"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::$MEDIA_BUCKET/*"]}]}""",
                )
            }
        }

    /** What image addresses start with in tests: the bucket read anonymously over path-style S3. */
    val mediaPublicBaseUrl: String get() = "${minio.s3URL}/$MEDIA_BUCKET"

    /** Topics are created as they are first used, as in local development. */
    val kafka = KafkaContainer("apache/kafka:4.1.0").apply { start() }

    private val accessTokenSecret = Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))

    /** One row of [sql] as column name to value, or null. For checking what an API call left behind. */
    fun row(sql: String, vararg args: Any): Map<String, Any?>? =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
                statement.executeQuery().use { rows ->
                    if (!rows.next()) return null
                    (1..rows.metaData.columnCount).associate { rows.metaData.getColumnLabel(it) to rows.getObject(it) }
                }
            }
        }

    /** Runs [sql] as the superuser. For state no API can produce yet, such as a suspended account. */
    fun execute(sql: String, vararg args: Any) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
                statement.executeUpdate()
            }
        }
    }

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("spring.datasource.url") { postgres.jdbcUrl }
        registry.add("spring.datasource.username") { postgres.username }
        registry.add("spring.datasource.password") { postgres.password }

        registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
        registry.add("contentria.messaging.relay.interval") { "100ms" }

        registry.add("contentria.media.public-base-url") { mediaPublicBaseUrl }
        registry.add("contentria.media.storage.endpoint") { minio.s3URL }
        registry.add("contentria.media.storage.bucket") { MEDIA_BUCKET }
        registry.add("contentria.media.storage.region") { "us-east-1" }
        registry.add("contentria.media.storage.access-key-id") { minio.userName }
        registry.add("contentria.media.storage.secret-access-key") { minio.password }

        registry.add("contentria.member.access-token.secret") { accessTokenSecret }
        registry.add("contentria.member.cookie.secure") { false }
        registry.add("contentria.member.mail.mode") { "log" }
        registry.add("contentria.member.social.login-success-url") { LOGIN_SUCCESS_URL }
        registry.add("contentria.member.social.login-failure-url") { LOGIN_FAILURE_URL }
        registry.add("contentria.member.social.link-result-url") { LINK_RESULT_URL }

        // Boot keeps the rest of Google's settings, including its issuer, which the fake signs as.
        registry.add("spring.security.oauth2.client.registration.google.client-id") { FakeOidcProvider.CLIENT_ID }
        registry.add("spring.security.oauth2.client.registration.google.client-secret") { "test-${UUID.randomUUID()}" }
        registry.add("spring.security.oauth2.client.provider.google.authorization-uri") { google.url("/authorize") }
        registry.add("spring.security.oauth2.client.provider.google.token-uri") { google.url("/token") }
        registry.add("spring.security.oauth2.client.provider.google.jwk-set-uri") { google.url("/jwks") }
    }
}
