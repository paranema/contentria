package com.contentria.app.migrator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

/**
 * Provisions a PostgreSQL 17 database with the real scripts in infra/database/contentria-v2,
 * then migrates it the way the deployment Job does: one Flyway per context, as that context's
 * migration role. The assertions are the stage-1 enforcement of
 * docs/architecture/04-persistence-conventions.md.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigratorIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:17")

    private val databaseUrl: String
        get() = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/$DATABASE"

    @BeforeAll
    fun provisionAndMigrate() {
        postgres.start()
        runScript("01-database-and-roles.sql", database = "postgres")
        runScript("02-schemas-and-grants.sql", database = DATABASE)

        Migrator(databaseUrl) { Credentials("${it.context}_migrator", MIGRATOR_PASSWORD) }.migrateAll()
    }

    @AfterAll
    fun stop() = postgres.stop()

    @Test
    @DisplayName("다시 실행하면 적용할 마이그레이션이 없다")
    fun `migrating twice applies nothing the second time`() {
        val results = Migrator(databaseUrl) { Credentials("${it.context}_migrator", MIGRATOR_PASSWORD) }.migrateAll()

        assertThat(results).allSatisfy { assertThat(it.migrationsExecuted).isZero() }
    }

    @Test
    @DisplayName("스키마를 넘는 외래키가 없다")
    fun `no foreign key crosses a schema`() {
        val crossing = superuser().use { connection ->
            connection.createStatement().executeQuery(CROSS_SCHEMA_FOREIGN_KEYS).use { rows ->
                buildList { while (rows.next()) add(rows.getString("conname")) }
            }
        }

        assertThat(crossing).isEmpty()
    }

    @Test
    @DisplayName("api_app은 member 테이블을 읽고 쓸 수 있다")
    fun `the runtime role has DML on member tables`() {
        connect("api_app", APP_PASSWORD).use { connection ->
            val id = UUID.randomUUID()
            connection.prepareStatement(
                "INSERT INTO member.profiles (id, display_name, created_at, updated_at) VALUES (?, ?, now(), now())",
            ).use { it.setObject(1, id); it.setString(2, "probe-$id".take(50)); it.executeUpdate() }

            val count = connection.prepareStatement("SELECT count(*) FROM member.profiles WHERE id = ?")
                .use { it.setObject(1, id); it.executeQuery().use { rows -> rows.next(); rows.getInt(1) } }
            assertThat(count).isEqualTo(1)

            connection.prepareStatement("DELETE FROM member.profiles WHERE id = ?")
                .use { it.setObject(1, id); it.executeUpdate() }
        }
    }

    @Test
    @DisplayName("api_app은 publishing 테이블도 읽고 쓸 수 있다")
    fun `the runtime role has DML on publishing tables`() {
        connect("api_app", APP_PASSWORD).use { connection ->
            val id = UUID.randomUUID()
            connection.prepareStatement(
                "INSERT INTO publishing.blogs (id, owner_id, slug, title, created_at, updated_at) VALUES (?, ?, ?, '제목', now(), now())",
            ).use { it.setObject(1, id); it.setObject(2, UUID.randomUUID()); it.setString(3, "probe-${id.toString().take(8)}"); it.executeUpdate() }

            connection.prepareStatement("DELETE FROM publishing.blogs WHERE id = ?")
                .use { it.setObject(1, id); assertThat(it.executeUpdate()).isEqualTo(1) }
        }
    }

    @Test
    @DisplayName("api_app은 interaction 테이블도 읽고 쓸 수 있다")
    fun `the runtime role has DML on interaction tables`() {
        connect("api_app", APP_PASSWORD).use { connection ->
            val id = UUID.randomUUID()
            connection.prepareStatement(
                "INSERT INTO interaction.subscriptions (id, subscriber_id, blog_id, created_at, updated_at) VALUES (?, ?, ?, now(), now())",
            ).use { it.setObject(1, id); it.setObject(2, UUID.randomUUID()); it.setObject(3, UUID.randomUUID()); it.executeUpdate() }

            connection.prepareStatement("DELETE FROM interaction.subscriptions WHERE id = ?")
                .use { it.setObject(1, id); assertThat(it.executeUpdate()).isEqualTo(1) }
        }
    }

    @Test
    @DisplayName("api_app은 notification 테이블도 읽고 쓸 수 있다")
    fun `the runtime role has DML on notification tables`() {
        connect("api_app", APP_PASSWORD).use { connection ->
            val id = UUID.randomUUID()
            connection.prepareStatement(
                "INSERT INTO notification.notifications (id, recipient_id, kind, post_id, blog_id, source_event_id, created_at) " +
                    "VALUES (?, ?, 'NEW_POST', ?, ?, ?, now())",
            ).use { statement ->
                statement.setObject(1, id)
                (2..5).forEach { statement.setObject(it, UUID.randomUUID()) }
                statement.executeUpdate()
            }

            connection.prepareStatement("UPDATE notification.notifications SET read_at = now() WHERE id = ?")
                .use { it.setObject(1, id); assertThat(it.executeUpdate()).isEqualTo(1) }
            connection.prepareStatement("DELETE FROM notification.notifications WHERE id = ?")
                .use { it.setObject(1, id); assertThat(it.executeUpdate()).isEqualTo(1) }
        }
    }

    @Test
    @DisplayName("api_app은 media 테이블도 읽고 쓸 수 있다")
    fun `the runtime role has DML on media tables`() {
        connect("api_app", APP_PASSWORD).use { connection ->
            val id = UUID.randomUUID()
            connection.prepareStatement(
                "INSERT INTO media.media (id, uploader_id, format, size_bytes, file_name, stored_key, status, created_at, updated_at) " +
                    "VALUES (?, ?, 'PNG', 1024, 'photo.png', ?, 'UPLOADING', now(), now())",
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, UUID.randomUUID())
                statement.setString(3, "images/$id.png")
                statement.executeUpdate()
            }
            connection.prepareStatement("UPDATE media.media SET status = 'READY', unattached_since = now() WHERE id = ?")
                .use { it.setObject(1, id); assertThat(it.executeUpdate()).isEqualTo(1) }
            connection.prepareStatement("INSERT INTO media.media_attachments (media_id, post_id) VALUES (?, ?)")
                .use { it.setObject(1, id); it.setObject(2, UUID.randomUUID()); it.executeUpdate() }

            connection.prepareStatement("DELETE FROM media.media WHERE id = ?")
                .use { it.setObject(1, id); assertThat(it.executeUpdate()).isEqualTo(1) }
        }
    }

    @Test
    @DisplayName("마이그레이션 롤은 다른 컨텍스트의 스키마에 손댈 수 없다")
    fun `a migration role cannot touch another context's schema`() {
        connect("publishing_migrator", MIGRATOR_PASSWORD).use { connection ->
            assertThatThrownBy { connection.createStatement().execute("CREATE TABLE member.forbidden (id int)") }
                .isInstanceOf(SQLException::class.java)
                .hasMessageContaining("permission denied")
            assertThatThrownBy { connection.createStatement().executeQuery("SELECT count(*) FROM member.accounts") }
                .isInstanceOf(SQLException::class.java)
                .hasMessageContaining("permission denied")
        }
    }

    @Test
    @DisplayName("batch_app은 잡이 있는 media만 읽고 쓰고, 다른 컨텍스트의 스키마에는 손댈 수 없다")
    fun `the batch role reaches only the schemas of its jobs`() {
        connect("batch_app", APP_PASSWORD).use { connection ->
            val id = UUID.randomUUID()
            connection.prepareStatement(
                "INSERT INTO media.media (id, uploader_id, format, size_bytes, file_name, stored_key, status, created_at, updated_at) " +
                    "VALUES (?, ?, 'PNG', 1024, 'photo.png', ?, 'UPLOADING', now(), now())",
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, UUID.randomUUID())
                statement.setString(3, "images/$id.png")
                statement.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM media.media WHERE id = ?")
                .use { it.setObject(1, id); assertThat(it.executeUpdate()).isEqualTo(1) }

            assertThatThrownBy { connection.createStatement().executeQuery("SELECT count(*) FROM publishing.posts") }
                .isInstanceOf(SQLException::class.java)
                .hasMessageContaining("permission denied")
            assertThatThrownBy { connection.createStatement().execute("CREATE TABLE media.forbidden (id int)") }
                .isInstanceOf(SQLException::class.java)
                .hasMessageContaining("permission denied")
        }
    }

    @Test
    @DisplayName("api_app은 방문을 기록하고, batch_app은 일별 통계를 집계하고 지난 방문을 지운다")
    fun `the runtime roles have DML on analytics tables`() {
        val visit = UUID.randomUUID()
        connect("api_app", APP_PASSWORD).use { connection ->
            connection.prepareStatement("INSERT INTO analytics.visits (id, blog_id, visitor_key, visited_at) VALUES (?, ?, ?, now())").use {
                it.setObject(1, visit)
                it.setObject(2, UUID.randomUUID())
                it.setString(3, "0123456789abcdef".repeat(4))
                it.executeUpdate()
            }
        }
        connect("batch_app", APP_PASSWORD).use { connection ->
            connection.prepareStatement(
                "INSERT INTO analytics.daily_statistics (blog_id, stat_date, visitors, views, aggregated_at) VALUES (?, CURRENT_DATE, 1, 1, now())",
            ).use { it.setObject(1, UUID.randomUUID()); it.executeUpdate() }
            connection.prepareStatement("DELETE FROM analytics.visits WHERE id = ?")
                .use { it.setObject(1, visit); assertThat(it.executeUpdate()).isEqualTo(1) }
        }
    }

    @Test
    @DisplayName("api_app은 테이블을 만들거나 바꿀 수 없다")
    fun `the runtime role has no DDL`() {
        connect("api_app", APP_PASSWORD).use { connection ->
            assertThatThrownBy { connection.createStatement().execute("CREATE TABLE member.forbidden (id int)") }
                .isInstanceOf(SQLException::class.java)
                .hasMessageContaining("permission denied")
        }
    }

    @Test
    @DisplayName("member_migrator는 다른 스키마를 참조하는 외래키를 만들 수 없다")
    fun `a migration role cannot reference another schema`() {
        superuser().use { connection ->
            connection.createStatement().execute(
                "CREATE SCHEMA IF NOT EXISTS other; CREATE TABLE IF NOT EXISTS other.things (id uuid PRIMARY KEY)",
            )
        }

        connect("member_migrator", MIGRATOR_PASSWORD).use { connection ->
            assertThatThrownBy {
                connection.createStatement().execute(
                    "ALTER TABLE member.profiles ADD CONSTRAINT fk_probe FOREIGN KEY (id) REFERENCES other.things (id)",
                )
            }.isInstanceOf(SQLException::class.java)
                .hasMessageContaining("permission denied")
        }
    }

    private fun runScript(name: String, database: String) {
        val target = "/provision/$name"
        postgres.copyFileToContainer(MountableFile.forHostPath(PROVISIONING_SCRIPTS.resolve(name)), target)
        val result = postgres.execInContainer(
            "psql", "-v", "ON_ERROR_STOP=1",
            "-U", postgres.username, "-d", database,
            "-v", "member_migrator_password=$MIGRATOR_PASSWORD",
            "-v", "publishing_migrator_password=$MIGRATOR_PASSWORD",
            "-v", "interaction_migrator_password=$MIGRATOR_PASSWORD",
            "-v", "notification_migrator_password=$MIGRATOR_PASSWORD",
            "-v", "media_migrator_password=$MIGRATOR_PASSWORD",
            "-v", "analytics_migrator_password=$MIGRATOR_PASSWORD",
            "-v", "api_app_password=$APP_PASSWORD",
            "-v", "batch_app_password=$APP_PASSWORD",
            "-f", target,
        )
        check(result.exitCode == 0) { "$name failed: ${result.stderr}" }
    }

    private fun superuser(): Connection = connect(postgres.username, postgres.password)

    private fun connect(username: String, password: String): Connection =
        DriverManager.getConnection(databaseUrl, username, password)

    private companion object {
        const val DATABASE = "contentria_v2"

        // Generated per run for roles inside the throwaway container. Never written down as a
        // literal, so nothing in the repository looks like a credential.
        val MIGRATOR_PASSWORD = "test-${UUID.randomUUID()}"
        val APP_PASSWORD = "test-${UUID.randomUUID()}"

        /** The Gradle test task runs in the module directory, backend/apps/migrator. */
        val PROVISIONING_SCRIPTS: Path = Path.of("../../../infra/database/contentria-v2").toAbsolutePath().normalize()

        /** docs/architecture/04-persistence-conventions.md, 1단계 강제 장치 */
        const val CROSS_SCHEMA_FOREIGN_KEYS = """
            SELECT con.conname
            FROM pg_constraint con
            JOIN pg_class src        ON src.oid = con.conrelid
            JOIN pg_namespace src_ns ON src_ns.oid = src.relnamespace
            JOIN pg_class dst        ON dst.oid = con.confrelid
            JOIN pg_namespace dst_ns ON dst_ns.oid = dst.relnamespace
            WHERE con.contype = 'f'
              AND src_ns.nspname <> dst_ns.nspname
        """
    }
}
