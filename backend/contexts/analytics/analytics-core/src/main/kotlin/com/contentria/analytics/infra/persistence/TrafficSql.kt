package com.contentria.analytics.infra.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Reads for the dashboard and view counts. Two sources, never both for one day: the visits from
 * [since] on (today), and the daily statistics before [today].
 */
@Component
class TrafficSql(jdbc: JdbcTemplate) {

    private val named = NamedParameterJdbcTemplate(jdbc)

    /** Visitors and views of [blogId] from [since] on, from the visits. */
    fun live(blogId: UUID, since: Instant): Counts = named.queryForObject(
        "SELECT count(DISTINCT visitor_key) AS visitors, count(*) AS views FROM analytics.visits WHERE blog_id = :blog AND visited_at >= :since",
        mapOf("blog" to blogId, "since" to Timestamp.from(since)),
    ) { rs, _ -> Counts(rs.getLong("visitors"), rs.getLong("views")) }!!

    /** The blog's daily totals in [from, to]. */
    fun days(blogId: UUID, from: LocalDate, to: LocalDate): Map<LocalDate, Counts> = named.query(
        "SELECT stat_date, visitors, views FROM analytics.daily_statistics " +
            "WHERE blog_id = :blog AND post_id IS NULL AND stat_date BETWEEN :from AND :to",
        mapOf("blog" to blogId, "from" to from, "to" to to),
    ) { rs, _ -> rs.getDate("stat_date").toLocalDate() to Counts(rs.getLong("visitors"), rs.getLong("views")) }.toMap()

    /** The blog's totals over every day before [today]. */
    fun totalBefore(blogId: UUID, today: LocalDate): Counts = named.queryForObject(
        "SELECT coalesce(sum(visitors), 0) AS visitors, coalesce(sum(views), 0) AS views FROM analytics.daily_statistics " +
            "WHERE blog_id = :blog AND post_id IS NULL AND stat_date < :today",
        mapOf("blog" to blogId, "today" to today),
    ) { rs, _ -> Counts(rs.getLong("visitors"), rs.getLong("views")) }!!

    /** Posts by views over [from, today), from the statistics, plus today's visits from [since]. */
    fun popular(blogId: UUID, from: LocalDate, today: LocalDate, since: Instant, limit: Int): List<Pair<UUID, Long>> = named.query(
        """
        SELECT post_id, sum(views) AS views FROM (
            SELECT post_id, views FROM analytics.daily_statistics
            WHERE blog_id = :blog AND post_id IS NOT NULL AND stat_date >= :from AND stat_date < :today
            UNION ALL
            SELECT post_id, count(*) AS views FROM analytics.visits
            WHERE blog_id = :blog AND post_id IS NOT NULL AND visited_at >= :since
            GROUP BY post_id
        ) counted
        GROUP BY post_id
        ORDER BY views DESC, post_id
        LIMIT :limit
        """.trimIndent(),
        mapOf("blog" to blogId, "from" to from, "today" to today, "since" to Timestamp.from(since), "limit" to limit),
    ) { rs, _ -> rs.getObject("post_id", UUID::class.java) to rs.getLong("views") }

    /** Each post's views over every day before [today] and from [since] on. Posts without any are absent. */
    fun postViews(postIds: Set<UUID>, today: LocalDate, since: Instant): Map<UUID, Long> = named.query(
        """
        SELECT post_id, sum(views) AS views FROM (
            SELECT post_id, views FROM analytics.daily_statistics WHERE post_id IN (:posts) AND stat_date < :today
            UNION ALL
            SELECT post_id, count(*) AS views FROM analytics.visits WHERE post_id IN (:posts) AND visited_at >= :since GROUP BY post_id
        ) counted
        GROUP BY post_id
        """.trimIndent(),
        mapOf("posts" to postIds, "today" to today, "since" to Timestamp.from(since)),
    ) { rs, _ -> rs.getObject("post_id", UUID::class.java) to rs.getLong("views") }.toMap()

    data class Counts(val visitors: Long, val views: Long) {
        operator fun plus(other: Counts) = Counts(visitors + other.visitors, views + other.views)

        companion object {
            val ZERO = Counts(0, 0)
        }
    }
}
