package com.contentria.app.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.http.HttpResponse
import java.util.UUID

/** docs/contexts/publishing/01-rules.md, 흐름 3~5와 글 slug, over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RecordingSenders::class)
class PublishingPostIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Test
    @DisplayName("새 글은 임시저장으로 시작하고, slug는 제목에서, 요약은 본문에서 만든다")
    fun `writing a draft`() {
        val (owner, slug) = blogger()

        val written = owner.post("/blogs/$slug/posts", post("코틀린 첫 글!", "**굵은** 첫 문장과 [링크](https://example.com)."))

        assertThat(written.statusCode()).isEqualTo(201)
        assertThat(written.field("status")).isEqualTo("DRAFT")
        assertThat(written.field("slug")).isEqualTo("코틀린-첫-글")
        assertThat(written.field("summary")).isEqualTo("굵은 첫 문장과 링크.")
        assertThat(written.body()).contains("\"publishedAt\":null", "\"summaryWrittenByAuthor\":false")

        val withSummary = owner.post("/blogs/$slug/posts", post("요약 있는 글", "본문", summary = "직접 쓴 요약"))
        assertThat(withSummary.field("summary")).isEqualTo("직접 쓴 요약")
    }

    @Test
    @DisplayName("임시저장 글은 제목을 따라 slug가 바뀌고, 본문만 고치면 그대로다")
    fun `a draft's slug follows its title`() {
        val (owner, slug) = blogger()
        val id = owner.post("/blogs/$slug/posts", post("처음 제목", "본문")).field("postId")

        assertThat(owner.put("/posts/$id", post("처음 제목", "고친 본문")).field("slug")).isEqualTo("처음-제목")

        val retitled = owner.put("/posts/$id", post("바꾼 제목", "고친 본문"))
        assertThat(retitled.field("slug")).isEqualTo("바꾼-제목")
        assertThat(retitled.body()).contains("\"oldSlugs\":[]")
    }

    @Test
    @DisplayName("발행한 글은 제목이 바뀌어도 slug가 그대로고, 바꾸면 옛 slug가 남는다. 발행 시각은 처음 발행 때 한 번이다")
    fun `publishing fixes the slug and the time`() {
        val (owner, slug) = blogger()
        val id = owner.post("/blogs/$slug/posts", post("발행할 글", "본문")).field("postId")

        val published = owner.post("/posts/$id/publish", "")
        assertThat(published.field("status")).isEqualTo("PUBLISHED")
        val publishedAt = published.field("publishedAt")

        assertThat(owner.put("/posts/$id", post("제목만 바꿈", "본문")).field("slug")).isEqualTo("발행할-글")

        val regenerated = owner.put("/posts/$id/slug", """{"slug":null}""")
        assertThat(regenerated.field("slug")).isEqualTo("제목만-바꿈")
        assertThat(regenerated.body()).contains("\"oldSlugs\":[\"발행할-글\"]")

        val typed = owner.put("/posts/$id/slug", """{"slug":"직접-고른-주소"}""")
        assertThat(typed.field("slug")).isEqualTo("직접-고른-주소")
        assertThat(owner.put("/posts/$id/slug", """{"slug":"발행할-글"}""").field("slug")).isEqualTo("발행할-글")

        assertThat(owner.post("/posts/$id/hide", "").field("status")).isEqualTo("PRIVATE")
        val republished = owner.post("/posts/$id/republish", "")
        assertThat(republished.field("status")).isEqualTo("PUBLISHED")
        assertThat(republished.field("publishedAt")).isEqualTo(publishedAt)

        val again = owner.post("/posts/$id/publish", "")
        assertThat(again.statusCode()).isEqualTo(409)
        assertThat(again.body()).contains("POST_STATUS_NOT_ALLOWED")
    }

    @Test
    @DisplayName("블로그 안에서 현재와 옛 slug는 겹치지 않는다. 같은 제목은 접미사를 붙이고, 남의 옛 slug는 쓸 수 없다")
    fun `slugs are unique in the blog`() {
        val (owner, slug) = blogger()
        val first = owner.post("/blogs/$slug/posts", post("같은 제목", "본문")).field("postId")
        assertThat(owner.post("/blogs/$slug/posts", post("같은 제목", "본문")).field("slug")).isEqualTo("같은-제목-2")

        owner.post("/posts/$first/publish", "")
        owner.put("/posts/$first/slug", """{"slug":"새-주소"}""")
        val other = owner.post("/blogs/$slug/posts", post("다른 글", "본문")).field("postId")

        val takingOld = owner.put("/posts/$other/slug", """{"slug":"같은-제목"}""")
        assertThat(takingOld.statusCode()).isEqualTo(409)
        assertThat(takingOld.body()).contains("POST_SLUG_TAKEN")
        assertThat(owner.put("/posts/$other/slug", """{"slug":"새-주소"}""").body()).contains("POST_SLUG_TAKEN")
        assertThat(owner.put("/posts/$other/slug", """{"slug":"Not Valid"}""").body()).contains("INVALID_POST_SLUG")

        // 다른 블로그라면 같은 slug를 쓸 수 있다
        val (elsewhere, otherBlog) = blogger()
        assertThat(elsewhere.post("/blogs/$otherBlog/posts", post("같은 제목", "본문")).field("slug")).isEqualTo("같은-제목")
    }

    @Test
    @DisplayName("카테고리는 같은 블로그의 것이어야 하고, 없어도 된다")
    fun `categories belong to the blog`() {
        val (owner, slug) = blogger()
        val (_, otherBlog) = blogger()
        val foreign = categoryId(otherBlog, "일상")
        val own = categoryId(slug, "독서")

        assertThat(owner.post("/blogs/$slug/posts", post("글", "본문", categoryId = foreign)).body()).contains("CATEGORY_NOT_IN_BLOG")
        assertThat(owner.post("/blogs/$slug/posts", post("글", "본문", categoryId = own)).field("categoryId")).isEqualTo(own)
        assertThat(owner.post("/blogs/$slug/posts", post("분류 없는 글", "본문")).body()).contains("\"categoryId\":null")
    }

    @Test
    @DisplayName("소유자만 글을 쓰고 본다. 남의 글은 ID로 물어도 없는 글과 같은 답이다")
    fun `only the owner`() {
        val (owner, slug) = blogger()
        val id = owner.post("/blogs/$slug/posts", post("내 글", "본문")).field("postId")
        val stranger = TestBrowser(port).also { it.signUpAsNewMember(codes) }

        assertThat(stranger.post("/blogs/$slug/posts", post("남의 블로그에", "본문")).body()).contains("NOT_BLOG_OWNER")
        listOf(
            stranger.get("/me/posts/$id"),
            stranger.put("/posts/$id", post("가로채기", "본문")),
            stranger.post("/posts/$id/publish", ""),
            stranger.delete("/posts/$id"),
        ).forEach { assertThat(it.statusCode()).isEqualTo(404) }
        assertThat(stranger.get("/me/posts/${UUID.randomUUID()}").body()).contains("POST_NOT_FOUND")

        assertThat(owner.get("/me/posts/$id").field("title")).isEqualTo("내 글")
        assertThat(owner.get("/me/blogs/$slug/posts").body()).contains("내 글", "책 한 권을 읽고 남기는 첫 기록")
        assertThat(TestBrowser(port).post("/blogs/$slug/posts", post("익명", "본문")).statusCode()).isEqualTo(401)
    }

    @Test
    @DisplayName("글을 지우면 현재와 옛 slug도 함께 사라진다")
    fun `deleting a post`() {
        val (owner, slug) = blogger()
        val id = owner.post("/blogs/$slug/posts", post("지울 글", "본문")).field("postId")
        owner.post("/posts/$id/publish", "")
        owner.put("/posts/$id/slug", """{"slug":"옮긴-주소"}""")

        assertThat(owner.delete("/posts/$id").statusCode()).isEqualTo(204)
        assertThat(owner.get("/me/posts/$id").statusCode()).isEqualTo(404)
        assertThat(ApiTestEnvironment.row("select count(*) as n from publishing.post_slugs where post_id = ?", UUID.fromString(id))!!["n"]).isEqualTo(0L)

        // 지운 글의 주소는 다시 쓸 수 있다
        assertThat(owner.post("/blogs/$slug/posts", post("지울 글", "본문")).field("slug")).isEqualTo("지울-글")
    }

    @Test
    @DisplayName("정지된 계정은 글을 쓰거나 발행할 수 없다")
    fun `a suspended blogger cannot write posts`() {
        val owner = TestBrowser(port)
        val ownerId = owner.signUpAsNewMember(codes)
        val slug = newSlug().also { owner.post("/blogs", """{"slug":"$it","title":"제목"}""") }
        val id = owner.post("/blogs/$slug/posts", post("글", "본문")).field("postId")

        ApiTestEnvironment.execute("update member.accounts set status = 'SUSPENDED' where id = ?", ownerId)

        assertThat(owner.post("/blogs/$slug/posts", post("새 글", "본문")).body()).contains("ACCOUNT_NOT_ACTIVE")
        assertThat(owner.post("/posts/$id/publish", "").body()).contains("ACCOUNT_NOT_ACTIVE")
    }

    @Test
    @DisplayName("처음 발행할 때와 지울 때만 이벤트를 아웃박스에 남긴다. 다시 공개, 거부된 요청, 샘플 글은 남기지 않는다")
    fun `post events in the outbox`() {
        val owner = TestBrowser(port)
        val ownerId = owner.signUpAsNewMember(codes)
        val slug = newSlug()
        val blogId = owner.post("/blogs", """{"slug":"$slug","title":"제목"}""").field("blogId")
        assertThat(eventsOfBlog(blogId)).isZero()

        val id = owner.post("/blogs/$slug/posts", post("이벤트 글", "본문")).field("postId")
        owner.post("/posts/$id/publish", "")
        owner.post("/posts/$id/hide", "")
        owner.post("/posts/$id/republish", "")
        assertThat(owner.post("/posts/$id/publish", "").statusCode()).isEqualTo(409)
        assertThat(owner.delete("/posts/$id").statusCode()).isEqualTo(204)

        val events = ApiTestEnvironment.row(
            "select string_agg(event_type, ',' order by occurred_at, id) as types, count(distinct aggregate_type) as kinds " +
                "from publishing.outbox_event where aggregate_id = ?",
            UUID.fromString(id),
        )!!
        assertThat(events["types"]).isEqualTo("PostPublished,PostDeleted")
        assertThat(events["kinds"]).isEqualTo(1L)

        val published = ApiTestEnvironment.row(
            "select payload->>'ownerId' as owner, payload->>'blogId' as blog, event_version as version, published_at as sent " +
                "from publishing.outbox_event where aggregate_id = ? and event_type = 'PostPublished'",
            UUID.fromString(id),
        )!!
        assertThat(published["owner"]).isEqualTo(ownerId.toString())
        assertThat(published["blog"]).isEqualTo(blogId)
        assertThat(published["version"]).isEqualTo(1)
        assertThat(published["sent"]).isNull()
    }

    @Test
    @DisplayName("정지된 계정의 발행은 거부되고 이벤트도 없다")
    fun `a refused publication leaves no event`() {
        val owner = TestBrowser(port)
        val ownerId = owner.signUpAsNewMember(codes)
        val slug = newSlug()
        val blogId = owner.post("/blogs", """{"slug":"$slug","title":"제목"}""").field("blogId")
        val id = owner.post("/blogs/$slug/posts", post("글", "본문")).field("postId")
        ApiTestEnvironment.execute("update member.accounts set status = 'SUSPENDED' where id = ?", ownerId)

        assertThat(owner.post("/posts/$id/publish", "").statusCode()).isEqualTo(403)
        assertThat(eventsOfBlog(blogId)).isZero()
    }

    private fun eventsOfBlog(blogId: String): Long =
        ApiTestEnvironment.row("select count(*) as n from publishing.outbox_event where payload->>'blogId' = ?", blogId)!!["n"] as Long

    /** A signed-in member who owns a fresh blog. */
    private fun blogger(): Pair<TestBrowser, String> {
        val browser = TestBrowser(port).also { it.signUpAsNewMember(codes) }
        val slug = newSlug()
        check(browser.post("/blogs", """{"slug":"$slug","title":"제목"}""").statusCode() == 201)
        return browser to slug
    }

    private fun categoryId(blogSlug: String, name: String): String =
        Regex("\\{\"id\":\"([^\"]+)\",\"name\":\"$name\"").find(TestBrowser(port).get("/blogs/$blogSlug/categories").body())!!.groupValues[1]

    private fun post(title: String, body: String, summary: String? = null, categoryId: String? = null): String =
        """{"title":"$title","body":"$body","summary":${summary?.let { "\"$it\"" } ?: "null"},"categoryId":${categoryId?.let { "\"$it\"" } ?: "null"}}"""

    private fun newSlug() = "blog-${UUID.randomUUID().toString().take(8)}"

    private fun TestBrowser.put(path: String, json: String): HttpResponse<String> = send("PUT", path, json)

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = ApiTestEnvironment.register(registry)
    }
}
