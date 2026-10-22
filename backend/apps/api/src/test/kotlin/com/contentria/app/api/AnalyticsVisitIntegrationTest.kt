package com.contentria.app.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.UUID

/** Recording readers' visits (analytics 01-rules 흐름 1). */
class AnalyticsVisitIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Test
    @DisplayName("로그인하지 않은 독자의 블로그 홈과 발행된 글 방문을 센다. 방문자 키와 유입 도메인만 남는다")
    fun `anonymous visits count`() {
        val blog = blogger()
        val post = publish(blog)

        assertThat(visit(TestBrowser(port), blog.id, null, referrer = "https://www.google.com/search?q=contentria").statusCode()).isEqualTo(204)
        assertThat(visit(TestBrowser(port), blog.id, post).statusCode()).isEqualTo(204)

        val home = ApiTestEnvironment.row(
            "select visitor_key, referrer_host from analytics.visits where blog_id = ? and post_id is null", blog.id,
        )!!
        assertThat(home["visitor_key"] as String).matches("[0-9a-f]{64}").doesNotContain("127.0.0.1")
        assertThat(home["referrer_host"]).isEqualTo("www.google.com")
        assertThat(visitsTo(blog.id, post)).isEqualTo(1)
    }

    @Test
    @DisplayName("같은 방문자가 30분 안에 같은 곳을 다시 열면 세지 않는다. 다른 글, 다른 브라우저, 30분 뒤는 센다")
    fun `thirty minutes`() {
        val blog = blogger()
        val first = publish(blog)
        val second = publish(blog)
        val reader = TestBrowser(port)

        visit(reader, blog.id, first)
        visit(reader, blog.id, first)
        assertThat(visitsTo(blog.id, first)).isEqualTo(1)

        visit(reader, blog.id, second)
        assertThat(visitsTo(blog.id, second)).isEqualTo(1)

        visit(reader, blog.id, first, userAgent = "Another Browser/1.0")
        assertThat(visitsTo(blog.id, first)).isEqualTo(2)

        ApiTestEnvironment.execute("update analytics.visits set visited_at = visited_at - interval '31 minutes' where post_id = ?", first)
        visit(reader, blog.id, first)
        assertThat(visitsTo(blog.id, first)).isEqualTo(3)
    }

    @Test
    @DisplayName("우리 프록시(사설 주소)가 넘긴 X-Forwarded-For의 주소로 방문자를 가른다. 같은 브라우저라도 주소가 다르면 다른 방문자다")
    fun `the forwarded address tells readers apart`() {
        val blog = blogger()
        val post = publish(blog)
        // The test client connects from 127.0.0.1, a private address, as Next.js does in the cluster
        val first = mapOf("User-Agent" to "Same Browser/1.0", "X-Forwarded-For" to "203.0.113.10")
        val second = mapOf("User-Agent" to "Same Browser/1.0", "X-Forwarded-For" to "203.0.113.20")

        val body = """{"blogId":"${blog.id}","postId":"$post"}"""
        TestBrowser(port).post("/analytics/visits", body, first)
        TestBrowser(port).post("/analytics/visits", body, first)
        assertThat(visitsTo(blog.id, post)).isEqualTo(1)

        TestBrowser(port).post("/analytics/visits", body, second)
        assertThat(visitsTo(blog.id, post)).isEqualTo(2)
    }

    @Test
    @DisplayName("블로그 소유자의 방문은 세지 않는다. 다른 회원의 방문은 센다")
    fun `the owner's own visits`() {
        val blog = blogger()
        val post = publish(blog)

        visit(blog.owner, blog.id, post)
        visit(blog.owner, blog.id, null)
        assertThat(visitsTo(blog.id, post)).isZero()
        assertThat(visitsTo(blog.id, null)).isZero()

        val member = TestBrowser(port).also { it.signUpAsNewMember(codes) }
        visit(member, blog.id, post)
        assertThat(visitsTo(blog.id, post)).isEqualTo(1)
    }

    @Test
    @DisplayName("없는 블로그, 임시저장, 비공개 글, 다른 블로그의 글은 세지 않는다. 그래도 204로 답한다")
    fun `what does not count`() {
        val blog = blogger()
        val other = blogger()
        val draft = UUID.fromString(blog.owner.post("/blogs/${blog.slug}/posts", draftOf("임시저장")).field("postId"))
        val hidden = publish(blog).also { blog.owner.post("/posts/$it/hide", "") }
        val othersPost = publish(other)
        val reader = TestBrowser(port)

        assertThat(visit(reader, UUID.randomUUID(), null).statusCode()).isEqualTo(204)
        assertThat(visit(reader, blog.id, draft).statusCode()).isEqualTo(204)
        assertThat(visit(reader, blog.id, hidden).statusCode()).isEqualTo(204)
        assertThat(visit(reader, blog.id, othersPost).statusCode()).isEqualTo(204)
        assertThat(visit(reader, blog.id, UUID.randomUUID()).statusCode()).isEqualTo(204)

        assertThat(ApiTestEnvironment.row("select count(*) as n from analytics.visits where blog_id = ?", blog.id)!!["n"]).isEqualTo(0L)
        assertThat(reader.post("/analytics/visits", """{"postId":"x"}""").field("code")).isEqualTo("INVALID_REQUEST")
    }

    private class Blog(val owner: TestBrowser, val slug: String, val id: UUID)

    private fun blogger(): Blog {
        val owner = TestBrowser(port).also { it.signUpAsNewMember(codes) }
        val slug = "blog-${UUID.randomUUID().toString().take(8)}"
        val created = owner.post("/blogs", """{"slug":"$slug","title":"제목"}""")
        check(created.statusCode() == 201)
        return Blog(owner, slug, UUID.fromString(created.field("blogId")))
    }

    private fun publish(blog: Blog): UUID {
        val id = blog.owner.post("/blogs/${blog.slug}/posts", draftOf("글 ${UUID.randomUUID().toString().take(8)}")).field("postId")
        check(blog.owner.post("/posts/$id/publish", "").statusCode() == 200)
        return UUID.fromString(id)
    }

    private fun draftOf(title: String) = """{"title":"$title","body":"본문","categoryId":null}"""

    private fun visit(browser: TestBrowser, blogId: UUID, postId: UUID?, referrer: String? = null, userAgent: String = "Reader Browser/1.0") =
        browser.post(
            "/analytics/visits",
            """{"blogId":"$blogId","postId":${postId?.let { "\"$it\"" } ?: "null"},"referrer":${referrer?.let { "\"$it\"" } ?: "null"}}""",
            mapOf("User-Agent" to userAgent),
        )

    private fun visitsTo(blogId: UUID, postId: UUID?): Long {
        val row = if (postId == null) {
            ApiTestEnvironment.row("select count(*) as n from analytics.visits where blog_id = ? and post_id is null", blogId)
        } else {
            ApiTestEnvironment.row("select count(*) as n from analytics.visits where blog_id = ? and post_id = ?", blogId, postId)
        }
        return row!!["n"] as Long
    }
}
