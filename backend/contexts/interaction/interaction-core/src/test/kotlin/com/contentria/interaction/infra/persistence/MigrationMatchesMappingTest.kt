package com.contentria.interaction.infra.persistence

import com.contentria.interaction.domain.InteractionSchema
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
            .schemas(InteractionSchema.NAME)
            .defaultSchema(InteractionSchema.NAME)
            .locations("classpath:db/migration/${InteractionSchema.NAME}")
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
            MetadataSources(registry).withInteractionEntities().buildMetadata().buildSessionFactory().close()
        }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("한 회원은 한 블로그를 한 번, 한 글을 한 번만 좋아요한다")
    fun `one subscription and one like each`() {
        val member = UUID.randomUUID()
        val blog = UUID.randomUUID()
        val post = UUID.randomUUID()
        execute("INSERT INTO interaction.subscriptions VALUES ('${UUID.randomUUID()}', '$member', '$blog', now(), now())")
        execute("INSERT INTO interaction.likes VALUES ('${UUID.randomUUID()}', '$member', '$post', '$blog', now(), now())")

        assertThatThrownBy { execute("INSERT INTO interaction.subscriptions VALUES ('${UUID.randomUUID()}', '$member', '$blog', now(), now())") }
            .hasMessageContaining("uq_subscriptions_subscriber_blog")
        assertThatThrownBy { execute("INSERT INTO interaction.likes VALUES ('${UUID.randomUUID()}', '$member', '$post', '$blog', now(), now())") }
            .hasMessageContaining("uq_likes_account_post")
    }

    @Test
    @DisplayName("지운 자리만 본문이 비어 있고, 답글은 지운 자리가 될 수 없다")
    fun `placeholder constraints`() {
        val parent = UUID.randomUUID()

        assertThatThrownBy { insertComment(body = "", removed = false) }.hasMessageContaining("ck_comments_removed_body")
        assertThatThrownBy { insertComment(body = "남은 말", removed = true) }.hasMessageContaining("ck_comments_removed_body")
        assertThatThrownBy { insertComment(body = "", removed = true, parent = parent) }.hasMessageContaining("ck_comments_placeholder_top_level")
        insertComment(body = "", removed = true)
        insertComment(body = "답글", removed = false, parent = parent)
    }

    private fun insertComment(body: String, removed: Boolean, parent: UUID? = null) =
        execute(
            "INSERT INTO interaction.comments (id, post_id, blog_id, commenter_id, parent_id, body, edited, removed, created_at, updated_at) " +
                "VALUES ('${UUID.randomUUID()}', '${UUID.randomUUID()}', '${UUID.randomUUID()}', '${UUID.randomUUID()}', " +
                "${parent?.let { "'$it'" } ?: "null"}, '$body', false, $removed, now(), now())",
        )

    private fun execute(sql: String) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { it.createStatement().execute(sql) }
    }
}
