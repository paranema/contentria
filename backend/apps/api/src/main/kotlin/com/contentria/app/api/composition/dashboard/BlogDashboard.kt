package com.contentria.app.api.composition.dashboard

import com.contentria.analytics.api.DailyTraffic
import com.contentria.analytics.api.GetBlogTrafficQuery
import com.contentria.analytics.api.TrafficSummary
import com.contentria.publishing.api.GetBlogReferencesQuery
import com.contentria.publishing.api.GetPostOverviewsQuery
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The blog dashboard, put together from publishing and analytics (docs/strategic/04-context-map.md,
 * 조회 조합; docs/contexts/analytics/01-rules.md, 흐름 3). Neither context knows the other's part:
 * publishing says whose blog it is and what the posts are called, analytics gives the numbers.
 */
@Component
class BlogDashboard(
    private val blogs: GetBlogReferencesQuery,
    private val posts: GetPostOverviewsQuery,
    private val traffic: GetBlogTrafficQuery,
) {

    fun of(requesterId: UUID, blogSlug: String, range: DashboardRange): DashboardView {
        // 흐름 3의 1번: someone else's blog answers as one that does not exist.
        val blog = blogs.getBlogBySlug(blogSlug)?.takeIf { it.ownerId == requesterId } ?: throw DashboardBlogNotFound()

        // Asked for more than shown: a popular post deleted since has no title and drops out.
        val ranked = traffic.popularPosts(blog.blogId, POPULAR_DAYS, POPULAR_SHOWN * 2)
        val titles = posts.getTitles(ranked.map { it.postId }.toSet())
        val popular = ranked.mapNotNull { entry ->
            titles[entry.postId]?.let { PopularPostView(it.postId, it.title, it.slug, entry.views) }
        }.take(POPULAR_SHOWN)

        return DashboardView(
            summary = traffic.summary(blog.blogId),
            trend = traffic.trend(blog.blogId, range.days),
            popularPosts = popular,
            publishedPosts = posts.countPublished(blog.blogId),
        )
    }

    private companion object {
        /** 01-rules, 처음 정하는 값: the last 30 days' top five. */
        const val POPULAR_DAYS = 30
        const val POPULAR_SHOWN = 5
    }
}

/** 01-rules, 처음 정하는 값 */
enum class DashboardRange(val value: String, val days: Int) {
    TWO_WEEKS("2weeks", 14),
    THIRTY_DAYS("30days", 30),
    NINETY_DAYS("90days", 90),
    ;

    companion object {
        fun of(value: String): DashboardRange = entries.firstOrNull { it.value == value } ?: throw InvalidDashboardRange()
    }
}

data class DashboardView(
    val summary: TrafficSummary,
    val trend: List<DailyTraffic>,
    val popularPosts: List<PopularPostView>,
    val publishedPosts: Long,
)

data class PopularPostView(val postId: UUID, val title: String, val slug: String, val views: Long)

class DashboardBlogNotFound : RuntimeException("No such blog of yours")

class InvalidDashboardRange : RuntimeException("The range is 2weeks, 30days or 90days")
