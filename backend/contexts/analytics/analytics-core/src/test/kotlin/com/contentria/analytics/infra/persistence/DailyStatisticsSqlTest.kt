package com.contentria.analytics.infra.persistence

import com.contentria.analytics.domain.AnalyticsSchema
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DailyStatisticsSqlTest {

    private val postgres = PostgreSQLContainer("postgres:17")
    private lateinit var jdbc: JdbcTemplate
    private lateinit var sql: DailyStatisticsSql

    private val seoul = ZoneId.of("Asia/Seoul")
    private val day = LocalDate.parse("2026-10-20")
    private val start = day.atStartOfDay(seoul).toInstant()
    private val end = day.plusDays(1).atStartOfDay(seoul).toInstant()

    @BeforeAll
    fun migrate() {
        postgres.start()
        Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas(AnalyticsSchema.NAME).defaultSchema(AnalyticsSchema.NAME)
            .locations("classpath:db/migration/${AnalyticsSchema.NAME}").load().migrate()
        jdbc = JdbcTemplate(DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password))
        sql = DailyStatisticsSql(jdbc)
    }

    @AfterAll
    fun stop() = postgres.stop()

    private fun key(n: Int) = n.toString(16).padStart(64, '0')

    private fun visit(blog: UUID, post: UUID?, visitor: Int, at: Instant) {
        jdbc.update(
            "INSERT INTO analytics.visits (id, blog_id, post_id, visitor_key, visited_at) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), blog, post, key(visitor), Timestamp.from(at),
        )
    }

    /** visitors/views of the blog's total (post null) or of a post, on [day]. */
    private fun counts(blog: UUID, post: UUID?, on: LocalDate = day): Pair<Long, Long>? =
        jdbc.query(
            "SELECT visitors, views FROM analytics.daily_statistics WHERE blog_id = ? AND post_id IS NOT DISTINCT FROM ? AND stat_date = ?",
            { rs, _ -> rs.getLong("visitors") to rs.getLong("views") },
            blog, post, on,
        ).singleOrNull()

    private fun rowsOf(blog: UUID): Int =
        jdbc.queryForObject("SELECT count(*) FROM analytics.daily_statistics WHERE blog_id = ?", Int::class.java, blog)!!

    @Test
    @DisplayName("블로그 전체와 글마다 방문자 수와 조회수를 센다. 블로그 홈 방문은 전체에만 들어간다")
    fun `the blog's total and each post`() {
        val blog = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val noon = start.plusSeconds(12 * 3600)
        visit(blog, null, 1, noon)
        visit(blog, null, 2, noon)
        visit(blog, first, 1, noon)
        visit(blog, first, 1, noon.plusSeconds(3600))
        visit(blog, second, 3, noon)

        sql.aggregate(day, start, end, Instant.now())

        assertThat(counts(blog, null)).isEqualTo(3L to 5L)
        assertThat(counts(blog, first)).isEqualTo(1L to 2L)
        assertThat(counts(blog, second)).isEqualTo(1L to 1L)
        assertThat(rowsOf(blog)).isEqualTo(3)
    }

    @Test
    @DisplayName("하루는 한국 시각 자정에서 자정까지다")
    fun `a Korean day`() {
        val blog = UUID.randomUUID()
        visit(blog, null, 1, start.minusSeconds(1))
        visit(blog, null, 2, start)
        visit(blog, null, 3, end.minusSeconds(1))
        visit(blog, null, 4, end)

        sql.aggregate(day, start, end, Instant.now())

        assertThat(counts(blog, null)).isEqualTo(2L to 2L)
        assertThat(Instant.parse("2026-10-19T15:00:00Z")).isEqualTo(start)
    }

    @Test
    @DisplayName("같은 날을 다시 집계하면 같은 행이 새 값으로 바뀐다")
    fun `aggregating again replaces the day`() {
        val blog = UUID.randomUUID()
        val post = UUID.randomUUID()
        visit(blog, post, 1, start.plusSeconds(60))
        sql.aggregate(day, start, end, Instant.now())
        sql.aggregate(day, start, end, Instant.now())
        assertThat(counts(blog, post)).isEqualTo(1L to 1L)

        visit(blog, post, 2, start.plusSeconds(120))
        sql.aggregate(day, start, end, Instant.now())

        assertThat(counts(blog, post)).isEqualTo(2L to 2L)
        assertThat(counts(blog, null)).isEqualTo(2L to 2L)
        assertThat(rowsOf(blog)).isEqualTo(2)
    }

    @Test
    @DisplayName("기준 시각 전의 방문만 지운다")
    fun `old visits go`() {
        val blog = UUID.randomUUID()
        val cutoff = Instant.parse("2026-07-22T00:00:00Z")
        visit(blog, null, 1, cutoff.minusSeconds(1))
        visit(blog, null, 2, cutoff)

        sql.deleteVisitsBefore(cutoff)

        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics.visits WHERE blog_id = ?", Int::class.java, blog)).isEqualTo(1)
    }
}
