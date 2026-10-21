package com.contentria.app.api

import com.contentria.analytics.api.GetViewCountsQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * The blog dashboard put together from publishing and analytics (analytics 01-rules 흐름 3, 4).
 * Today's visits go through HTTP; past days are written as the daily job would.
 */
class BlogDashboardIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Autowired
    private lateinit var viewCounts: GetViewCountsQuery

    private val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

    @Test
    @DisplayName("오늘은 방문 기록에서, 지난날은 일별 통계에서 세어 요약, 추이, 인기 글, 발행한 글 수를 한 번에 준다")
    fun `the dashboard`() {
        val blog = blogger()
        val a = publish(blog, "가 글")
        val b = publish(blog, "나 글")
        val c = publish(blog, "다 글")
        val deleted = publish(blog, "지운 글")
        visit(blog.id, a, "Reader/1")
        visit(blog.id, a, "Reader/2")
        visit(blog.id, b, "Reader/1")
        visit(blog.id, null, "Reader/3")
        daily(blog.id, null, today.minusDays(1), visitors = 5, views = 7)
        daily(blog.id, null, today.minusDays(10), visitors = 2, views = 3)
        daily(blog.id, a, today.minusDays(1), visitors = 3, views = 4)
        daily(blog.id, c, today.minusDays(20), visitors = 9, views = 10)
        daily(blog.id, deleted, today.minusDays(1), visitors = 40, views = 50)
        daily(blog.id, b, today.minusDays(40), visitors = 90, views = 100)
        blog.owner.delete("/posts/$deleted")

        val response = blog.owner.get("/me/blogs/${blog.slug}/dashboard")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains(
            "\"todayVisitors\":3", "\"todayViews\":4",
            "\"yesterdayVisitors\":5", "\"yesterdayViews\":7",
            "\"totalVisitors\":10", "\"totalViews\":14",
            "\"publishedPosts\":3",
        )
        val trend = Regex("\\{\"date\":\"([0-9-]+)\",\"visitors\":(\\d+),\"views\":(\\d+)}").findAll(response.body())
            .associate { LocalDate.parse(it.groupValues[1]) to (it.groupValues[2].toLong() to it.groupValues[3].toLong()) }
        assertThat(trend).hasSize(14)
        assertThat(trend.keys.first()).isEqualTo(today.minusDays(13))
        assertThat(trend[today]).isEqualTo(3L to 4L)
        assertThat(trend[today.minusDays(1)]).isEqualTo(5L to 7L)
        assertThat(trend[today.minusDays(10)]).isEqualTo(2L to 3L)
        assertThat(trend[today.minusDays(2)]).isEqualTo(0L to 0L)

        val popular = Regex("\"postId\":\"([^\"]+)\",\"title\":\"([^\"]+)\",\"slug\":\"[^\"]+\",\"views\":(\\d+)").findAll(response.body())
            .map { Triple(UUID.fromString(it.groupValues[1]), it.groupValues[2], it.groupValues[3].toLong()) }.toList()
        assertThat(popular).containsExactly(Triple(c, "다 글", 10L), Triple(a, "가 글", 6L), Triple(b, "나 글", 1L))
    }

    @Test
    @DisplayName("기간은 2주, 30일, 90일이고 다른 값은 거부한다")
    fun `ranges`() {
        val blog = blogger()

        assertThat(trendSize(blog, "30days")).isEqualTo(30)
        assertThat(trendSize(blog, "90days")).isEqualTo(90)
        val invalid = blog.owner.get("/me/blogs/${blog.slug}/dashboard?range=1year")
        assertThat(invalid.statusCode()).isEqualTo(400)
        assertThat(invalid.field("code")).isEqualTo("INVALID_REQUEST")
    }

    @Test
    @DisplayName("남의 블로그와 없는 블로그는 같은 404이고, 로그인하지 않으면 볼 수 없다")
    fun `only the owner`() {
        val blog = blogger()
        val other = TestBrowser(port).also { it.signUpAsNewMember(codes) }

        val notMine = other.get("/me/blogs/${blog.slug}/dashboard")
        assertThat(notMine.statusCode()).isEqualTo(404)
        assertThat(notMine.field("code")).isEqualTo("BLOG_NOT_FOUND")
        assertThat(blog.owner.get("/me/blogs/no-such-blog/dashboard").statusCode()).isEqualTo(404)
        assertThat(TestBrowser(port).get("/me/blogs/${blog.slug}/dashboard").statusCode()).isEqualTo(401)
    }

    @Test
    @DisplayName("글마다 누적 조회수는 지난 일별 통계와 오늘 방문의 합이고, 방문이 없는 글은 0이다")
    fun `view counts`() {
        val blog = blogger()
        val a = publish(blog, "가 글")
        val b = publish(blog, "나 글")
        visit(blog.id, a, "Reader/1")
        visit(blog.id, a, "Reader/2")
        daily(blog.id, a, today.minusDays(1), visitors = 3, views = 4)
        daily(blog.id, b, today.minusDays(200), visitors = 1, views = 1)
        val unknown = UUID.randomUUID()

        assertThat(viewCounts.viewCounts(setOf(a, b, unknown))).isEqualTo(mapOf(a to 6L, b to 1L, unknown to 0L))
        assertThat(viewCounts.viewCounts(emptySet())).isEmpty()
    }

    private class Blog(val owner: TestBrowser, val slug: String, val id: UUID)

    private fun blogger(): Blog {
        val owner = TestBrowser(port).also { it.signUpAsNewMember(codes) }
        val slug = "blog-${UUID.randomUUID().toString().take(8)}"
        val created = owner.post("/blogs", """{"slug":"$slug","title":"제목"}""")
        check(created.statusCode() == 201)
        return Blog(owner, slug, UUID.fromString(created.field("blogId")))
    }

    private fun publish(blog: Blog, title: String): UUID {
        val id = blog.owner.post("/blogs/${blog.slug}/posts", """{"title":"$title","body":"본문","categoryId":null}""").field("postId")
        check(blog.owner.post("/posts/$id/publish", "").statusCode() == 200)
        return UUID.fromString(id)
    }

    /** A reader who is not signed in, told apart by [userAgent]. */
    private fun visit(blogId: UUID, postId: UUID?, userAgent: String) {
        val response = TestBrowser(port).post(
            "/analytics/visits",
            """{"blogId":"$blogId","postId":${postId?.let { "\"$it\"" } ?: "null"}}""",
            mapOf("User-Agent" to userAgent),
        )
        check(response.statusCode() == 204)
    }

    /** A past day's statistics, as the daily job writes them; [postId] null for the blog's total. */
    private fun daily(blogId: UUID, postId: UUID?, day: LocalDate, visitors: Long, views: Long) {
        if (postId == null) {
            ApiTestEnvironment.execute(
                "insert into analytics.daily_statistics (blog_id, post_id, stat_date, visitors, views, aggregated_at) values (?, null, ?, ?, ?, now())",
                blogId, day, visitors, views,
            )
        } else {
            ApiTestEnvironment.execute(
                "insert into analytics.daily_statistics (blog_id, post_id, stat_date, visitors, views, aggregated_at) values (?, ?, ?, ?, ?, now())",
                blogId, postId, day, visitors, views,
            )
        }
    }

    private fun trendSize(blog: Blog, range: String): Int =
        Regex("\\{\"date\":").findAll(blog.owner.get("/me/blogs/${blog.slug}/dashboard?range=$range").body()).count()
}
