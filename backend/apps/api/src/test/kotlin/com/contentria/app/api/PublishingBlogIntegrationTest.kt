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

/** docs/contexts/publishing/01-rules.md, 흐름 1과 흐름 2, over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RecordingSenders::class)
class PublishingBlogIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Test
    @DisplayName("블로그를 만들면 샘플 카테고리와 임시저장 샘플 글이 함께 생긴다")
    fun `a new blog comes with samples`() {
        val owner = TestBrowser(port)
        val ownerId = owner.signUpAsNewMember(codes)
        val slug = newSlug()

        val created = owner.post("/blogs", """{"slug":"  ${slug.uppercase()} ","title":"내 블로그","description":"소개"}""")
        assertThat(created.statusCode()).isEqualTo(201)
        assertThat(created.field("slug")).isEqualTo(slug)
        val blogId = UUID.fromString(created.field("blogId"))

        assertThat(owner.get("/me/blogs").body()).contains(slug)
        assertThat(TestBrowser(port).get("/blogs/$slug").field("title")).isEqualTo("내 블로그")
        assertThat(TestBrowser(port).get("/blogs/$slug").field("ownerId")).isEqualTo(ownerId.toString())

        val tree = TestBrowser(port).get("/blogs/$slug/categories")
        assertThat(names(tree)).containsExactly("기록", "독서", "일상")
        assertThat(tree.body()).contains("\"slug\":\"독서\"")

        val samples = ApiTestEnvironment.row(
            "select count(*) as total, count(*) filter (where status = 'DRAFT') as drafts, count(*) filter (where category_id is null) as uncategorised " +
                "from publishing.posts where blog_id = ?",
            blogId,
        )!!
        assertThat(samples).containsEntry("total", 2L).containsEntry("drafts", 2L).containsEntry("uncategorised", 0L)
    }

    @Test
    @DisplayName("주소 형식, 예약어, 중복, 회원당 3개를 거부한다")
    fun `blog creation refusals`() {
        val owner = TestBrowser(port).also { it.signUpAsNewMember(codes) }

        assertThat(owner.post("/blogs", blog("ab")).body()).contains("INVALID_BLOG_SLUG")
        assertThat(owner.post("/blogs", blog("notice")).body()).contains("BLOG_SLUG_RESERVED")

        val taken = newSlug()
        assertThat(owner.post("/blogs", blog(taken)).statusCode()).isEqualTo(201)
        assertThat(TestBrowser(port).also { it.signUpAsNewMember(codes) }.post("/blogs", blog(taken)).body()).contains("BLOG_SLUG_TAKEN")

        repeat(2) { assertThat(owner.post("/blogs", blog(newSlug())).statusCode()).isEqualTo(201) }
        val fourth = owner.post("/blogs", blog(newSlug()))
        assertThat(fourth.statusCode()).isEqualTo(409)
        assertThat(fourth.body()).contains("BLOG_LIMIT_REACHED")

        assertThat(TestBrowser(port).post("/blogs", blog(newSlug())).statusCode()).isEqualTo(401)
    }

    @Test
    @DisplayName("정지된 계정은 블로그를 만들 수도, 고칠 수도 없다")
    fun `a suspended blogger cannot write`() {
        val owner = TestBrowser(port)
        val ownerId = owner.signUpAsNewMember(codes)
        val slug = newSlug().also { assertThat(owner.post("/blogs", blog(it)).statusCode()).isEqualTo(201) }

        // 정지 API가 아직 없다. 액세스 토큰은 정지 뒤에도 만료까지 통과하므로 출판이 직접 회원에게 묻는다.
        ApiTestEnvironment.execute("update member.accounts set status = 'SUSPENDED' where id = ?", ownerId)

        assertThat(owner.post("/blogs", blog(newSlug())).body()).contains("ACCOUNT_NOT_ACTIVE")
        assertThat(owner.patch("/blogs/$slug", """{"title":"바뀐 제목"}""").body()).contains("ACCOUNT_NOT_ACTIVE")
        assertThat(TestBrowser(port).get("/blogs/$slug").field("title")).isEqualTo("제목")
    }

    @Test
    @DisplayName("제목과 설명은 소유자만 바꾼다")
    fun `settings belong to the owner`() {
        val owner = TestBrowser(port).also { it.signUpAsNewMember(codes) }
        val slug = newSlug().also { owner.post("/blogs", blog(it)) }

        val changed = owner.patch("/blogs/$slug", """{"title":"  새 제목 ","description":"  "}""")
        assertThat(changed.statusCode()).isEqualTo(200)
        assertThat(changed.field("title")).isEqualTo("새 제목")
        assertThat(changed.body()).contains("\"description\":null")

        val stranger = TestBrowser(port).also { it.signUpAsNewMember(codes) }
        assertThat(stranger.patch("/blogs/$slug", """{"title":"남의 것"}""").body()).contains("NOT_BLOG_OWNER")
        assertThat(owner.patch("/blogs/no-such-blog-${UUID.randomUUID().toString().take(6)}", """{"title":"t"}""").statusCode()).isEqualTo(404)
    }

    @Test
    @DisplayName("카테고리 트리 전체를 버전과 함께 저장한다. 이름을 바꾸고, 옮기고, 더할 수 있다")
    fun `edit the category tree`() {
        val owner = TestBrowser(port).also { it.signUpAsNewMember(codes) }
        val slug = newSlug().also { owner.post("/blogs", blog(it)) }
        val tree = TestBrowser(port).get("/blogs/$slug/categories")
        val (record, reading, daily) = listOf("기록", "독서", "일상").map { idOf(tree, it) }

        val saved = owner.put(
            "/blogs/$slug/categories",
            edit(
                version(tree),
                """{"key":"$daily","name":"하루"}""",
                """{"key":"$record","name":"기록"}""",
                """{"key":"$reading","name":"독서","parentKey":"$record"}""",
                """{"key":"new-1","name":"영화","parentKey":"$record"}""",
            ),
        )

        assertThat(saved.statusCode()).isEqualTo(200)
        assertThat(names(saved)).containsExactly("하루", "기록", "독서", "영화")
        assertThat(version(saved)).isGreaterThan(version(tree))
        assertThat(idOf(saved, "하루")).isEqualTo(daily)
    }

    @Test
    @DisplayName("다른 곳에서 먼저 저장했으면 옛 버전으로 저장할 수 없다")
    fun `a stale editor cannot overwrite`() {
        val owner = TestBrowser(port).also { it.signUpAsNewMember(codes) }
        val slug = newSlug().also { owner.post("/blogs", blog(it)) }
        val loaded = TestBrowser(port).get("/blogs/$slug/categories")
        val nodes = keep(loaded)

        assertThat(owner.put("/blogs/$slug/categories", edit(version(loaded), *nodes)).statusCode()).isEqualTo(200)
        val stale = owner.put("/blogs/$slug/categories", edit(version(loaded), *nodes))
        assertThat(stale.statusCode()).isEqualTo(409)
        assertThat(stale.body()).contains("CATEGORY_TREE_CHANGED")
    }

    @Test
    @DisplayName("깊이 2단계, 하위가 남은 삭제, 글이 있는 삭제를 거부하고, 소유자만 편집한다")
    fun `category edit refusals`() {
        val owner = TestBrowser(port).also { it.signUpAsNewMember(codes) }
        val slug = newSlug().also { owner.post("/blogs", blog(it)) }
        val tree = TestBrowser(port).get("/blogs/$slug/categories")
        val (record, reading, daily) = listOf("기록", "독서", "일상").map { idOf(tree, it) }
        val v = version(tree)

        val tooDeep = owner.put("/blogs/$slug/categories", edit(v, *keep(tree), """{"key":"x","name":"소설","parentKey":"$reading"}"""))
        assertThat(tooDeep.body()).contains("CATEGORY_TOO_DEEP")

        val orphaning = owner.put("/blogs/$slug/categories", edit(v, """{"key":"$reading","name":"독서","parentKey":"$record"}""", """{"key":"$daily","name":"일상"}"""))
        assertThat(orphaning.body()).contains("CATEGORY_HAS_CHILDREN")

        // 샘플 글이 일상에 있다
        val inUse = owner.put("/blogs/$slug/categories", edit(v, """{"key":"$record","name":"기록"}""", """{"key":"$reading","name":"독서","parentKey":"$record"}"""))
        assertThat(inUse.statusCode()).isEqualTo(409)
        assertThat(inUse.body()).contains("CATEGORIES_IN_USE")

        val stranger = TestBrowser(port).also { it.signUpAsNewMember(codes) }
        assertThat(stranger.put("/blogs/$slug/categories", edit(v, *keep(tree))).body()).contains("NOT_BLOG_OWNER")
        assertThat(names(TestBrowser(port).get("/blogs/$slug/categories"))).containsExactly("기록", "독서", "일상")
    }

    private fun blog(slug: String) = """{"slug":"$slug","title":"제목"}"""

    private fun newSlug() = "blog-${UUID.randomUUID().toString().take(8)}"

    private fun edit(version: Long, vararg nodes: String) = """{"version":$version,"categories":[${nodes.joinToString(",")}]}"""

    /** The tree as it is, as an edit that changes nothing. */
    private fun keep(tree: HttpResponse<String>): Array<String> =
        CATEGORY.findAll(tree.body()).map { m ->
            val (id, name, parent) = m.destructured
            if (parent == "null") """{"key":"$id","name":"$name"}""" else """{"key":"$id","name":"$name","parentKey":${parent}}"""
        }.toList().toTypedArray()

    private fun names(tree: HttpResponse<String>) = CATEGORY.findAll(tree.body()).map { it.groupValues[2] }.toList()

    private fun idOf(tree: HttpResponse<String>, name: String) = CATEGORY.findAll(tree.body()).single { it.groupValues[2] == name }.groupValues[1]

    private fun version(tree: HttpResponse<String>) = Regex("\"version\":(\\d+)").find(tree.body())!!.groupValues[1].toLong()

    private fun TestBrowser.put(path: String, json: String) = send("PUT", path, json)

    companion object {
        /** One category of a tree response: id, name, parentId (quoted or null). */
        private val CATEGORY = Regex("\\{\"id\":\"([^\"]+)\",\"name\":\"([^\"]+)\",\"slug\":\"[^\"]*\",\"parentId\":(null|\"[^\"]+\")")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = ApiTestEnvironment.register(registry)
    }
}
