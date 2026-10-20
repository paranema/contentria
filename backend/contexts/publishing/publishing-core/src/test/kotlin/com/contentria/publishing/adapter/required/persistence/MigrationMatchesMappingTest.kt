package com.contentria.publishing.adapter.required.persistence

import com.contentria.publishing.domain.PublishingSchema
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
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

/**
 * Applies this context's Flyway migrations to a real PostgreSQL and lets Hibernate validate the
 * mappings against the result, the check `ddl-auto: validate` runs at start-up. Also checks the
 * behaviour the migration adds beyond what the mappings can say: constraints checked at commit,
 * and the status and publication time agreeing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrationMatchesMappingTest {

    private val postgres = PostgreSQLContainer("postgres:17")

    @BeforeAll
    fun migrate() {
        postgres.start()
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas(PublishingSchema.NAME)
            .defaultSchema(PublishingSchema.NAME)
            .locations("classpath:db/migration/${PublishingSchema.NAME}")
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
            MetadataSources(registry).withPublishingEntities().buildMetadata().buildSessionFactory().close()
        }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("트리를 한 번에 저장할 때처럼, 같은 slug의 카테고리를 먼저 넣고 나중에 지워도 커밋에서 통과한다")
    fun `category slug uniqueness is checked at commit`() {
        val tree = UUID.randomUUID()
        val old = UUID.randomUUID()
        inTransaction { insertTree(tree); insertCategory(old, tree, "일상") }

        inTransaction {
            insertCategory(UUID.randomUUID(), tree, "일상")
            execute("DELETE FROM publishing.categories WHERE id = '$old'")
        }

        assertThatThrownBy { inTransaction { insertCategory(UUID.randomUUID(), tree, "일상") } }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("uq_categories_tree_slug")
    }

    @Test
    @DisplayName("자식 카테고리를 부모보다 먼저 넣어도 커밋에서 통과한다")
    fun `parent reference is checked at commit`() {
        val tree = UUID.randomUUID()
        val parent = UUID.randomUUID()

        inTransaction {
            insertTree(tree)
            insertCategory(UUID.randomUUID(), tree, "독서", parent)
            insertCategory(parent, tree, "기록")
        }
    }

    @Test
    @DisplayName("임시저장 글만 발행 시각이 없다")
    fun `only drafts lack a publication time`() {
        assertThatThrownBy { inTransaction { insertPost(status = "PUBLISHED", publishedAt = null) } }
            .hasMessageContaining("ck_posts_published_at")
        assertThatThrownBy { inTransaction { insertPost(status = "DRAFT", publishedAt = "now()") } }
            .hasMessageContaining("ck_posts_published_at")
        inTransaction { insertPost(status = "PRIVATE", publishedAt = "now()") }
    }

    @Test
    @DisplayName("한 블로그 안에서 현재와 옛 slug는 글이 달라도 겹칠 수 없다")
    fun `post slugs are unique per blog`() {
        val blog = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        inTransaction {
            insertPost(id = first, blog = blog, status = "DRAFT", publishedAt = null)
            insertPost(id = second, blog = blog, status = "DRAFT", publishedAt = null)
            insertSlug(first, blog, "옛-주소")
        }

        assertThatThrownBy { inTransaction { insertSlug(second, blog, "옛-주소") } }
            .hasMessageContaining("uq_post_slugs_blog_slug")
        inTransaction { insertSlug(second, UUID.randomUUID(), "옛-주소") }
    }

    private fun Connection.insertTree(id: UUID) =
        execute("INSERT INTO publishing.category_trees (id, version, created_at, updated_at) VALUES ('$id', 0, now(), now())")

    private fun Connection.insertCategory(id: UUID, tree: UUID, slug: String, parent: UUID? = null) =
        execute(
            "INSERT INTO publishing.categories (id, tree_id, name, slug, parent_id, display_order, created_at, updated_at) " +
                "VALUES ('$id', '$tree', '$slug', '$slug', ${parent?.let { "'$it'" } ?: "null"}, 0, now(), now())",
        )

    private fun Connection.insertPost(id: UUID = UUID.randomUUID(), blog: UUID = UUID.randomUUID(), status: String, publishedAt: String?) =
        execute(
            "INSERT INTO publishing.posts (id, blog_id, title, body, summary, summary_written, status, published_at, slug, created_at, updated_at) " +
                "VALUES ('$id', '$blog', '제목', '본문', '요약', false, '$status', ${publishedAt ?: "null"}, 'slug-$id', now(), now())",
        )

    private fun Connection.insertSlug(post: UUID, blog: UUID, slug: String) =
        execute("INSERT INTO publishing.post_slugs (id, post_id, blog_id, slug, created_at, updated_at) VALUES ('${UUID.randomUUID()}', '$post', '$blog', '$slug', now(), now())")

    private fun Connection.execute(sql: String) {
        createStatement().use { it.execute(sql) }
    }

    private fun inTransaction(block: Connection.() -> Unit) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.autoCommit = false
            try {
                connection.block()
                connection.commit()
            } catch (e: SQLException) {
                connection.rollback()
                throw e
            }
        }
    }
}
