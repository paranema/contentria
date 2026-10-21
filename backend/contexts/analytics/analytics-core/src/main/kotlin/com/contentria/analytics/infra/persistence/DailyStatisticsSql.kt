package com.contentria.analytics.infra.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * The daily statistics, written by SQL (docs/contexts/analytics/02-implementation.md, 애그리거트 경계):
 * a day is thousands of rows recomputed at once, and no entity is loaded for it.
 */
@Component
class DailyStatisticsSql(private val jdbc: JdbcTemplate) {

    /**
     * Counts the visits in [from, to) as [day]'s: one row for each blog's total and one for each of
     * its posts, replacing what the day had. Visits to a blog's home count in its total only; the
     * HAVING drops the group they would otherwise form, which would collide with the total.
     * Returns the number of rows written.
     */
    fun aggregate(day: LocalDate, from: Instant, to: Instant, at: Instant): Int = jdbc.update(
        """
        INSERT INTO analytics.daily_statistics (blog_id, post_id, stat_date, visitors, views, aggregated_at)
        SELECT blog_id, post_id, ?, count(DISTINCT visitor_key), count(*), ?
        FROM analytics.visits
        WHERE visited_at >= ? AND visited_at < ?
        GROUP BY GROUPING SETS ((blog_id, post_id), (blog_id))
        HAVING GROUPING(post_id) = 1 OR post_id IS NOT NULL
        ON CONFLICT ON CONSTRAINT uq_daily_statistics_blog_post_date
        DO UPDATE SET visitors = EXCLUDED.visitors, views = EXCLUDED.views, aggregated_at = EXCLUDED.aggregated_at
        """.trimIndent(),
        day, Timestamp.from(at), Timestamp.from(from), Timestamp.from(to),
    )

    /** 01-rules 흐름 2의 4번. Returns the number of visits removed. */
    fun deleteVisitsBefore(before: Instant): Int =
        jdbc.update("DELETE FROM analytics.visits WHERE visited_at < ?", Timestamp.from(before))
}
