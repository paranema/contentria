package com.contentria.app.batch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Date
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** The daily statistics job through a real PostgreSQL (analytics 01-rules 흐름 2). */
class AnalyticsDailyStatisticsJobIntegrationTest : BatchIntegrationTest() {

    @Autowired
    private lateinit var analyticsDailyStatisticsJob: Job

    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    @DisplayName("끝난 최근 3일을 블로그 전체와 글마다 집계하고, 오늘과 그 전은 건드리지 않는다. 90일 지난 방문은 지운다")
    fun `the last three days`() {
        val blog = UUID.randomUUID()
        val post = UUID.randomUUID()
        val today = LocalDate.now(seoul)
        (0..4).forEach { back -> visit(blog, post, at = noonOf(today.minusDays(back.toLong()))) }
        val old = visit(blog, null, at = Instant.now().minus(Duration.ofDays(91)))
        val kept = visit(blog, null, at = Instant.now().minus(Duration.ofDays(89)))

        assertThat(run(analyticsDailyStatisticsJob)).isEqualTo(BatchStatus.COMPLETED)

        val days = BatchTestEnvironment.rows(
            "SELECT stat_date, views FROM analytics.daily_statistics WHERE blog_id = ? AND post_id = ? ORDER BY stat_date", blog, post,
        ).map { (it["stat_date"] as Date).toLocalDate() }
        assertThat(days).containsExactly(today.minusDays(3), today.minusDays(2), today.minusDays(1))
        assertThat(BatchTestEnvironment.rows("SELECT 1 AS found FROM analytics.visits WHERE id = ?", old)).isEmpty()
        assertThat(BatchTestEnvironment.rows("SELECT 1 AS found FROM analytics.visits WHERE id = ?", kept)).hasSize(1)

        visit(blog, post, at = noonOf(today.minusDays(1)).plusSeconds(60))
        assertThat(run(analyticsDailyStatisticsJob)).isEqualTo(BatchStatus.COMPLETED)
        val yesterday = BatchTestEnvironment.rows(
            "SELECT visitors, views FROM analytics.daily_statistics WHERE blog_id = ? AND post_id = ? AND stat_date = ?",
            blog, post, today.minusDays(1),
        ).single()
        assertThat(yesterday["views"]).isEqualTo(2L)
        assertThat(yesterday["visitors"]).isEqualTo(2L)
    }

    private fun noonOf(day: LocalDate): Instant = day.atTime(12, 0).atZone(seoul).toInstant()

    private var visitors = 0

    /** A visit by a new visitor each time. */
    private fun visit(blog: UUID, post: UUID?, at: Instant): UUID {
        val id = UUID.randomUUID()
        BatchTestEnvironment.execute(
            "INSERT INTO analytics.visits (id, blog_id, post_id, visitor_key, visited_at) VALUES (?, ?, ?, ?, ?)",
            id, blog, post, (++visitors).toString(16).padStart(64, '0'), Timestamp.from(at),
        )
        return id
    }
}
