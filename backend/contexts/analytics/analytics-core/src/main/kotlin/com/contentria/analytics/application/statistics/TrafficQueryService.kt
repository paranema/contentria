package com.contentria.analytics.application.statistics

import com.contentria.analytics.api.DailyTraffic
import com.contentria.analytics.api.GetBlogTrafficQuery
import com.contentria.analytics.api.GetViewCountsQuery
import com.contentria.analytics.api.PostViews
import com.contentria.analytics.api.TrafficSummary
import com.contentria.analytics.infra.persistence.TrafficSql
import com.contentria.analytics.infra.persistence.TrafficSql.Counts
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * docs/contexts/analytics/01-rules.md, 흐름 3, 4. Today from the visits, past days from the daily
 * statistics. A past day the job has not counted yet reads 0 until it has. Its own read-only
 * transaction, as it will be behind a network call.
 */
@Service
class TrafficQueryService(
    private val traffic: TrafficSql,
    private val clock: Clock,
) : GetBlogTrafficQuery, GetViewCountsQuery {

    @Transactional(readOnly = true)
    override fun summary(blogId: UUID): TrafficSummary {
        val today = today()
        val live = traffic.live(blogId, startOf(today))
        val yesterday = traffic.days(blogId, today.minusDays(1), today.minusDays(1)).values.firstOrNull() ?: Counts.ZERO
        val total = traffic.totalBefore(blogId, today) + live
        return TrafficSummary(live.visitors, live.views, yesterday.visitors, yesterday.views, total.visitors, total.views)
    }

    @Transactional(readOnly = true)
    override fun trend(blogId: UUID, days: Int): List<DailyTraffic> {
        require(days in 1..MAX_DAYS) { "A trend covers 1 to $MAX_DAYS days" }
        val today = today()
        val first = today.minusDays(days - 1L)
        val past = traffic.days(blogId, first, today.minusDays(1))
        val live = traffic.live(blogId, startOf(today))
        return (0 until days).map { first.plusDays(it.toLong()) }.map { day ->
            val counts = if (day == today) live else past[day] ?: Counts.ZERO
            DailyTraffic(day, counts.visitors, counts.views)
        }
    }

    @Transactional(readOnly = true)
    override fun popularPosts(blogId: UUID, days: Int, limit: Int): List<PostViews> {
        require(days in 1..MAX_DAYS) { "Popular posts cover 1 to $MAX_DAYS days" }
        val today = today()
        return traffic.popular(blogId, today.minusDays(days - 1L), today, startOf(today), limit).map { (post, views) -> PostViews(post, views) }
    }

    @Transactional(readOnly = true)
    override fun viewCounts(postIds: Set<UUID>): Map<UUID, Long> {
        if (postIds.isEmpty()) return emptyMap()
        val today = today()
        val counted = traffic.postViews(postIds, today, startOf(today))
        return postIds.associateWith { counted[it] ?: 0L }
    }

    private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), DailyStatisticsService.SEOUL)

    private fun startOf(day: LocalDate) = day.atStartOfDay(DailyStatisticsService.SEOUL).toInstant()

    private companion object {
        /** The longest dashboard range, with room to spare. */
        const val MAX_DAYS = 366
    }
}
