package com.contentria.analytics.infra.persistence

import com.contentria.analytics.domain.AnalyticsSchema
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
    private val key = "0123456789abcdef".repeat(4)

    @BeforeAll
    fun migrate() {
        postgres.start()
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas(AnalyticsSchema.NAME)
            .defaultSchema(AnalyticsSchema.NAME)
            .locations("classpath:db/migration/${AnalyticsSchema.NAME}")
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
            MetadataSources(registry).withAnalyticsEntities().buildMetadata().buildSessionFactory().close()
        }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("방문자 키는 16진수 64자만 받는다. IP 같은 원문은 들어가지 않는다")
    fun `visitor key shape`() {
        execute("INSERT INTO analytics.visits VALUES ('${UUID.randomUUID()}', '${UUID.randomUUID()}', null, '$key', null, now())")

        assertThatThrownBy {
            execute("INSERT INTO analytics.visits VALUES ('${UUID.randomUUID()}', '${UUID.randomUUID()}', null, '203.0.113.7', null, now())")
        }.hasMessageContaining("ck_visits_visitor_key")
    }

    @Test
    @DisplayName("블로그 전체 행을 포함해 하루, 블로그, 글마다 한 행이고, 다시 집계하면 같은 행을 바꾼다")
    fun `one row a day, the blog's total included`() {
        val blog = UUID.randomUUID()
        val post = UUID.randomUUID()
        upsert(blog, null, visitors = 3, views = 5)
        upsert(blog, post, visitors = 2, views = 2)

        assertThatThrownBy { insert(blog, null, visitors = 1, views = 1) }.hasMessageContaining("uq_daily_statistics_blog_post_date")
        assertThatThrownBy { insert(blog, post, visitors = 1, views = 1) }.hasMessageContaining("uq_daily_statistics_blog_post_date")

        upsert(blog, null, visitors = 4, views = 9)
        assertThat(count("SELECT count(*) FROM analytics.daily_statistics WHERE blog_id = '$blog'")).isEqualTo(2)
        assertThat(count("SELECT views FROM analytics.daily_statistics WHERE blog_id = '$blog' AND post_id IS NULL")).isEqualTo(9)
    }

    @Test
    @DisplayName("수는 0 이상이고, 조회수는 방문자 수보다 적을 수 없다")
    fun `counts`() {
        assertThatThrownBy { insert(UUID.randomUUID(), null, visitors = -1, views = 0) }.hasMessageContaining("ck_daily_statistics_counts")
        assertThatThrownBy { insert(UUID.randomUUID(), null, visitors = 3, views = 2) }.hasMessageContaining("ck_daily_statistics_counts")
        insert(UUID.randomUUID(), null, visitors = 0, views = 0)
    }

    private fun insert(blog: UUID, post: UUID?, visitors: Long, views: Long) = execute(
        "INSERT INTO analytics.daily_statistics (blog_id, post_id, stat_date, visitors, views, aggregated_at) " +
            "VALUES ('$blog', ${post?.let { "'$it'" } ?: "null"}, DATE '2026-10-20', $visitors, $views, now())",
    )

    /** What the daily job does: the day's value replaces whatever was there. */
    private fun upsert(blog: UUID, post: UUID?, visitors: Long, views: Long) = execute(
        "INSERT INTO analytics.daily_statistics (blog_id, post_id, stat_date, visitors, views, aggregated_at) " +
            "VALUES ('$blog', ${post?.let { "'$it'" } ?: "null"}, DATE '2026-10-20', $visitors, $views, now()) " +
            "ON CONFLICT ON CONSTRAINT uq_daily_statistics_blog_post_date " +
            "DO UPDATE SET visitors = EXCLUDED.visitors, views = EXCLUDED.views, aggregated_at = EXCLUDED.aggregated_at",
    )

    private fun count(sql: String): Long =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().executeQuery(sql).use { it.next(); it.getLong(1) }
        }

    private fun execute(sql: String) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { it.createStatement().execute(sql) }
    }
}
