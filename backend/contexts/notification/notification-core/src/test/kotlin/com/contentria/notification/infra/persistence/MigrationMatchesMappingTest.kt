package com.contentria.notification.infra.persistence

import com.contentria.notification.domain.NotificationSchema
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
            .schemas(NotificationSchema.NAME)
            .defaultSchema(NotificationSchema.NAME)
            .locations("classpath:db/migration/${NotificationSchema.NAME}")
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
            MetadataSources(registry).withNotificationEntities().buildMetadata().buildSessionFactory().close()
        }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("같은 이벤트로 한 사람에게 알림을 두 번 만들 수 없다. 다른 사람에게는 만든다")
    fun `one notification per event and recipient`() {
        val event = UUID.randomUUID()
        val recipient = UUID.randomUUID()
        insert(recipient = recipient, event = event)
        insert(recipient = UUID.randomUUID(), event = event)

        assertThatThrownBy { insert(recipient = recipient, event = event) }.hasMessageContaining("uq_notifications_event_recipient")
    }

    @Test
    @DisplayName("새 글 알림은 댓글과 쓴 사람이 없고, 댓글 알림은 둘 다 있다")
    fun `fields follow the kind`() {
        val comment = UUID.randomUUID()
        val actor = UUID.randomUUID()

        insert(kind = "NEW_POST")
        insert(kind = "COMMENT_ON_POST", comment = comment, actor = actor)
        insert(kind = "REPLY_TO_COMMENT", comment = comment, actor = actor)

        assertThatThrownBy { insert(kind = "NEW_POST", comment = comment, actor = actor) }.hasMessageContaining("ck_notifications_comment_fields")
        assertThatThrownBy { insert(kind = "COMMENT_ON_POST") }.hasMessageContaining("ck_notifications_comment_fields")
        assertThatThrownBy { insert(kind = "COMMENT_ON_POST", comment = comment) }.hasMessageContaining("ck_notifications_comment_fields")
        assertThatThrownBy { insert(kind = "REPLY_TO_COMMENT", actor = actor) }.hasMessageContaining("ck_notifications_comment_fields")
        assertThatThrownBy { insert(kind = "POST_LIKED", comment = comment, actor = actor) }.hasMessageContaining("ck_notifications_kind")
    }

    private fun insert(
        kind: String = "NEW_POST",
        recipient: UUID = UUID.randomUUID(),
        event: UUID = UUID.randomUUID(),
        comment: UUID? = null,
        actor: UUID? = null,
    ) = execute(
        "INSERT INTO notification.notifications " +
            "(id, recipient_id, kind, post_id, blog_id, comment_id, actor_id, source_event_id, created_at) VALUES " +
            "('${UUID.randomUUID()}', '$recipient', '$kind', '${UUID.randomUUID()}', '${UUID.randomUUID()}', " +
            "${comment.sql()}, ${actor.sql()}, '$event', now())",
    )

    private fun UUID?.sql() = this?.let { "'$it'" } ?: "null"

    private fun execute(sql: String) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { it.createStatement().execute(sql) }
    }
}
