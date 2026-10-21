package com.contentria.media.adapter.required.persistence

import com.contentria.media.domain.MediaSchema
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID

/** Flyway on a real PostgreSQL, then Hibernate's validate, then the constraints the mappings cannot express. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrationMatchesMappingTest {

    private val postgres = PostgreSQLContainer("postgres:17")

    @BeforeAll
    fun migrate() {
        postgres.start()
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas(MediaSchema.NAME)
            .defaultSchema(MediaSchema.NAME)
            .locations("classpath:db/migration/${MediaSchema.NAME}")
            .failOnMissingLocations(true)
            .load()
            .migrate()
    }

    @AfterAll
    fun stop() = postgres.stop()

    @Test
    @DisplayName("마이그레이션으로 만든 스키마가 엔티티 매핑과 일치한다")
    fun `migrated schema matches the entity mappings`() {
        val registry = StandardServiceRegistryBuilder()
            .applySetting("jakarta.persistence.jdbc.url", postgres.jdbcUrl)
            .applySetting("jakarta.persistence.jdbc.user", postgres.username)
            .applySetting("jakarta.persistence.jdbc.password", postgres.password)
            .applySetting("hibernate.hbm2ddl.auto", "validate")
            .build()

        assertThatCode {
            MetadataSources(registry).withMediaEntities().buildMetadata().buildSessionFactory().close()
        }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("형식, 상태, 크기는 정한 값만 받고, 업로드 중인 이미지에는 정리 기준 시각이 없다")
    fun `column constraints`() {
        insert()
        insert(status = "READY", unattachedSince = "now()")

        assertThatThrownBy { insert(format = "SVG") }.hasMessageContaining("ck_media_format")
        assertThatThrownBy { insert(status = "DELETED") }.hasMessageContaining("ck_media_status")
        assertThatThrownBy { insert(size = 0) }.hasMessageContaining("ck_media_size")
        assertThatThrownBy { insert(size = 10L * 1024 * 1024 + 1) }.hasMessageContaining("ck_media_size")
        assertThatThrownBy { insert(unattachedSince = "now()") }.hasMessageContaining("ck_media_unattached_since")
    }

    @Test
    @DisplayName("한 파일에 기록은 하나다")
    fun `one record per file`() {
        val key = "images/${UUID.randomUUID()}.png"
        insert(key = key)

        assertThatThrownBy { insert(key = key) }.hasMessageContaining("uq_media_stored_key")
    }

    @Test
    @DisplayName("연결은 있는 이미지에만 걸 수 있고, 한 글에 한 번이며, 이미지를 지우면 함께 지워진다")
    fun `attachments belong to the image`() {
        val media = insert(status = "READY")
        val post = UUID.randomUUID()
        execute("INSERT INTO media.media_attachments VALUES ('$media', '$post')")
        execute("INSERT INTO media.media_attachments VALUES ('$media', '${UUID.randomUUID()}')")

        assertThatThrownBy { execute("INSERT INTO media.media_attachments VALUES ('$media', '$post')") }
            .hasMessageContaining("pk_media_attachments")
        assertThatThrownBy { execute("INSERT INTO media.media_attachments VALUES ('${UUID.randomUUID()}', '$post')") }
            .hasMessageContaining("fk_media_attachments_media")

        execute("DELETE FROM media.media WHERE id = '$media'")
        assertThat(count("SELECT count(*) FROM media.media_attachments WHERE media_id = '$media'")).isZero()
    }

    private fun insert(
        format: String = "PNG",
        status: String = "UPLOADING",
        size: Long = 1024,
        key: String = "images/${UUID.randomUUID()}.png",
        unattachedSince: String = "null",
    ): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO media.media (id, uploader_id, format, size_bytes, file_name, stored_key, status, unattached_since, created_at, updated_at) " +
                "VALUES ('$id', '${UUID.randomUUID()}', '$format', $size, 'photo.png', '$key', '$status', $unattachedSince, now(), now())",
        )
        return id
    }

    private fun count(sql: String): Long =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().executeQuery(sql).use { it.next(); it.getLong(1) }
        }

    private fun execute(sql: String) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { it.createStatement().execute(sql) }
    }
}
