package com.contentria.analytics.api

import java.time.LocalDate
import java.util.UUID

/**
 * A blog's numbers for its dashboard (docs/contexts/analytics/01-rules.md, 흐름 3). Days are Korean
 * days and "today" is analytics' own: today is counted from the visits as they come, past days
 * from the daily statistics. Whose blog it is, the caller checks.
 */
interface GetBlogTrafficQuery {

    fun summary(blogId: UUID): TrafficSummary

    /** The last [days] days ending today, oldest first; a day without visits is 0. */
    fun trend(blogId: UUID, days: Int): List<DailyTraffic>

    /** The posts most viewed in the last [days] days ending today, at most [limit], most viewed first. */
    fun popularPosts(blogId: UUID, days: Int, limit: Int): List<PostViews>
}

data class TrafficSummary(
    val todayVisitors: Long,
    val todayViews: Long,
    val yesterdayVisitors: Long,
    val yesterdayViews: Long,
    val totalVisitors: Long,
    val totalViews: Long,
)

data class DailyTraffic(val date: LocalDate, val visitors: Long, val views: Long)

data class PostViews(val postId: UUID, val views: Long)

/** 01-rules 흐름 4: each post's views since it was first visited. Every id asked has an entry; none is 0. */
interface GetViewCountsQuery {
    fun viewCounts(postIds: Set<UUID>): Map<UUID, Long>
}
