package com.contentria.app.api

import com.contentria.publishing.api.BlogReference
import com.contentria.publishing.api.GetBlogReferencesQuery
import com.contentria.publishing.api.GetPostReferencesQuery
import com.contentria.publishing.api.PostReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URLEncoder
import java.net.http.HttpResponse
import java.util.UUID

/** Reading as anyone: published posts only, pages, categories, and old slugs (01-rules 흐름 4, 글 slug). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RecordingSenders::class)
class PublishingReadingIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Autowired
    private lateinit var postReferences: GetPostReferencesQuery

    @Autowired
    private lateinit var blogReferences: GetBlogReferencesQuery

    private val reader get() = TestBrowser(port)

    @Test
    @DisplayName("블로그의 공개 목록에는 발행된 글만 최신순으로 나온다")
    fun `only published posts, newest first`() {
        val (owner, blog) = blogger()
        val first = publish(owner, blog, "첫 공개 글")
        val second = publish(owner, blog, "둘째 공개 글")
        val hidden = publish(owner, blog, "숨길 글").also { owner.post("/posts/$it/hide", "") }

        val page = reader.get("/blogs/$blog/posts")

        assertThat(page.statusCode()).isEqualTo(200)
        assertThat(ids(page)).containsExactly(second, first)
        assertThat(page.body()).doesNotContain(hidden, "책 한 권을 읽고 남기는 첫 기록")
        assertThat(page.body()).contains("\"nextCursor\":null")
    }

    @Test
    @DisplayName("20개씩 나누고, 같은 발행 시각의 글이 페이지 경계에 걸려도 빠지거나 겹치지 않는다")
    fun `keyset pages`() {
        val (owner, blog) = blogger()
        val all = (1..23).map { publish(owner, blog, "글 $it") }
        // 모두 같은 시각에 발행된 것으로 만들어 ID만으로 순서가 갈리게 한다
        ApiTestEnvironment.execute(
            "update publishing.posts set published_at = '2026-01-01T00:00:00Z' where status = 'PUBLISHED' and blog_id = (select id from publishing.blogs where slug = ?)",
            blog,
        )

        val firstPage = reader.get("/blogs/$blog/posts")
        assertThat(ids(firstPage)).hasSize(20)
        val cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(firstPage.body())!!.groupValues[1]

        val secondPage = reader.get("/blogs/$blog/posts?cursor=$cursor")
        assertThat(ids(secondPage)).hasSize(3)
        assertThat(secondPage.body()).contains("\"nextCursor\":null")
        assertThat(ids(firstPage) + ids(secondPage)).containsExactlyInAnyOrderElementsOf(all).doesNotHaveDuplicates()

        assertThat(reader.get("/blogs/$blog/posts?cursor=not-a-cursor").body()).contains("INVALID_CURSOR")
    }

    @Test
    @DisplayName("상위 카테고리로 거르면 하위 카테고리의 글도 나온다")
    fun `category filter covers children`() {
        val (owner, blog) = blogger()
        val reading = publish(owner, blog, "독서 기록", category = "독서")
        val daily = publish(owner, blog, "하루 기록", category = "일상")

        assertThat(ids(reader.get("/blogs/$blog/posts?category=${enc("기록")}"))).containsExactly(reading)
        assertThat(ids(reader.get("/blogs/$blog/posts?category=${enc("독서")}"))).containsExactly(reading)
        assertThat(ids(reader.get("/blogs/$blog/posts?category=${enc("일상")}"))).containsExactly(daily)
        assertThat(reader.get("/blogs/$blog/posts?category=nowhere").body()).contains("CATEGORY_NOT_FOUND")
    }

    @Test
    @DisplayName("발행된 글만 상세를 보여 준다. 임시저장, 비공개 글은 없는 글과 같다")
    fun `post detail`() {
        val (owner, blog) = blogger()
        val id = publish(owner, blog, "읽을 글", body = "**본문**입니다")
        val draft = owner.post("/blogs/$blog/posts", post("임시저장 글")).field("slug")

        val found = reader.get("/blogs/$blog/posts/${enc("읽을-글")}")
        assertThat(found.statusCode()).isEqualTo(200)
        assertThat(found.field("postId")).isEqualTo(id)
        assertThat(found.field("body")).isEqualTo("**본문**입니다")

        assertThat(reader.get("/blogs/$blog/posts/${enc(draft)}").statusCode()).isEqualTo(404)
        owner.post("/posts/$id/hide", "")
        assertThat(reader.get("/blogs/$blog/posts/${enc("읽을-글")}").statusCode()).isEqualTo(404)
        assertThat(reader.get("/blogs/no-such-${UUID.randomUUID().toString().take(6)}/posts/x").statusCode()).isEqualTo(404)
    }

    @Test
    @DisplayName("옛 slug로 오면 현재 주소로 영구 이동을 알린다")
    fun `an old slug redirects`() {
        val (owner, blog) = blogger()
        val id = publish(owner, blog, "옮길 글")
        owner.send("PUT", "/posts/$id/slug", """{"slug":"새-주소"}""")

        val moved = reader.get("/blogs/$blog/posts/${enc("옮길-글")}")

        assertThat(moved.statusCode()).isEqualTo(301)
        assertThat(moved.location()).endsWith("/api/blogs/$blog/posts/${enc("새-주소")}")
        assertThat(moved.field("currentSlug")).isEqualTo("새-주소")
        assertThat(reader.get("/blogs/$blog/posts/${enc("새-주소")}").statusCode()).isEqualTo(200)
    }

    @Test
    @DisplayName("홈의 최근 글은 모든 블로그의 발행된 글이고, 로그인하지 않아도 본다")
    fun `recent posts across blogs`() {
        val (owner, blog) = blogger()
        val (other, otherBlog) = blogger()
        val mine = publish(owner, blog, "내 블로그의 글")
        val theirs = publish(other, otherBlog, "다른 블로그의 글")
        owner.post("/blogs/$blog/posts", post("홈에 안 나올 임시저장 글"))

        val recent = reader.get("/posts")

        assertThat(recent.statusCode()).isEqualTo(200)
        assertThat(ids(recent).take(2)).containsExactly(theirs, mine)
        assertThat(recent.body()).contains("\"blogSlug\":\"$otherBlog\"").doesNotContain("홈에 안 나올 임시저장 글")
    }

    @Test
    @DisplayName("소유자의 목록은 임시저장까지 최근 수정 순으로, 같은 커서로 나눈다")
    fun `the owner's list pages too`() {
        val (owner, blog) = blogger()
        val latest = owner.post("/blogs/$blog/posts", post("방금 쓴 글")).field("postId")

        val page = owner.get("/me/blogs/$blog/posts")

        assertThat(ids(page).first()).isEqualTo(latest)
        assertThat(ids(page)).hasSize(3)
        assertThat(page.body()).contains("\"status\":\"DRAFT\"", "\"nextCursor\":null")
    }

    @Test
    @DisplayName("다른 컨텍스트에는 글의 블로그, 소유자, 공개 여부를 ID 묶음으로 알려준다. 모르는 ID는 결과에 없다")
    fun `references for other contexts`() {
        val owner = TestBrowser(port)
        val ownerId = owner.signUpAsNewMember(codes)
        val blog = "blog-${UUID.randomUUID().toString().take(8)}"
        val blogId = UUID.fromString(owner.post("/blogs", """{"slug":"$blog","title":"제목"}""").field("blogId"))
        val published = UUID.fromString(publish(owner, blog, "공개 글"))
        val draft = UUID.fromString(owner.post("/blogs/$blog/posts", post("임시저장 글")).field("postId"))
        val unknown = UUID.randomUUID()

        val references = postReferences.getPosts(setOf(published, draft, unknown))

        assertThat(references.keys).containsExactlyInAnyOrder(published, draft)
        assertThat(references.getValue(published)).isEqualTo(PostReference(published, blogId, ownerId, isPublic = true))
        assertThat(references.getValue(draft).isPublic).isFalse()
        assertThat(postReferences.getPosts(emptySet())).isEmpty()

        assertThat(blogReferences.getBlogBySlug(blog)).isEqualTo(BlogReference(blogId, blog, ownerId))
        assertThat(blogReferences.getBlogs(setOf(blogId, unknown)).keys).containsExactly(blogId)
        assertThat(blogReferences.getBlogBySlug("no-such-blog")).isNull()
    }

    private fun blogger(): Pair<TestBrowser, String> {
        val browser = TestBrowser(port).also { it.signUpAsNewMember(codes) }
        val slug = "blog-${UUID.randomUUID().toString().take(8)}"
        check(browser.post("/blogs", """{"slug":"$slug","title":"제목"}""").statusCode() == 201)
        return browser to slug
    }

    /** Writes and publishes a post; returns its id. */
    private fun publish(owner: TestBrowser, blog: String, title: String, body: String = "본문", category: String? = null): String {
        val categoryId = category?.let { name ->
            Regex("\\{\"id\":\"([^\"]+)\",\"name\":\"$name\"").find(reader.get("/blogs/$blog/categories").body())!!.groupValues[1]
        }
        val id = owner.post("/blogs/$blog/posts", post(title, body, categoryId)).field("postId")
        check(owner.post("/posts/$id/publish", "").statusCode() == 200)
        return id
    }

    private fun post(title: String, body: String = "본문", categoryId: String? = null) =
        """{"title":"$title","body":"$body","categoryId":${categoryId?.let { "\"$it\"" } ?: "null"}}"""

    private fun ids(page: HttpResponse<String>): List<String> =
        Regex("\"postId\":\"([^\"]+)\"").findAll(page.body()).map { it.groupValues[1] }.toList()

    private fun enc(segment: String): String = URLEncoder.encode(segment, Charsets.UTF_8)

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = ApiTestEnvironment.register(registry)
    }
}
