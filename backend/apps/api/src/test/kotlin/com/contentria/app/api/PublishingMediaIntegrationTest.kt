package com.contentria.app.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.UUID

/**
 * Saving and deleting posts keeps their images attached (docs/architecture/03-context-integration.md,
 * 글 저장과 이미지 연결; media 01-rules 흐름 3, 4; publishing 01-rules 본문의 이미지).
 */
class PublishingMediaIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Test
    @DisplayName("글을 쓰면 본문의 내 이미지가 그 글에 연결된다. 다른 사이트의 이미지는 넘어간다")
    fun `writing attaches`() {
        val blog = blogger()
        val first = blog.owner.uploadPng()
        val second = blog.owner.uploadPng()

        val post = write(blog, body(first.url, second.url, "https://elsewhere.test/photo.jpg"))

        assertThat(postsOf(first)).containsExactly(post)
        assertThat(postsOf(second)).containsExactly(post)
        assertThat(unattachedSince(first)).isNull()
    }

    @Test
    @DisplayName("본문에서 빼고 고치면 연결이 풀리고 그때부터 정리를 센다. 다시 넣으면 다시 연결된다")
    fun `revising lets go and takes back`() {
        val blog = blogger()
        val kept = blog.owner.uploadPng()
        val dropped = blog.owner.uploadPng()
        val post = write(blog, body(kept.url, dropped.url))

        revise(blog, post, body(kept.url))
        assertThat(postsOf(kept)).containsExactly(post)
        assertThat(postsOf(dropped)).isEmpty()
        assertThat(unattachedSince(dropped)).isNotNull()

        revise(blog, post, body(kept.url, dropped.url))
        assertThat(postsOf(dropped)).containsExactly(post)
        assertThat(unattachedSince(dropped)).isNull()
    }

    @Test
    @DisplayName("한 이미지는 같은 회원의 여러 글에 쓰이고, 한 글이 지워져도 다른 글의 연결은 남는다")
    fun `two posts, one image`() {
        val blog = blogger()
        val image = blog.owner.uploadPng()
        val first = write(blog, body(image.url))
        val second = write(blog, body(image.url))
        assertThat(postsOf(image)).containsExactlyInAnyOrder(first, second)

        assertThat(blog.owner.delete("/posts/$first").statusCode()).isEqualTo(204)
        assertThat(postsOf(image)).containsExactly(second)
        assertThat(unattachedSince(image)).isNull()

        blog.owner.delete("/posts/$second")
        assertThat(postsOf(image)).isEmpty()
        assertThat(unattachedSince(image)).isNotNull()
    }

    @Test
    @DisplayName("남이 올린 이미지와 완료하지 않은 업로드는 연결하지 않는다")
    fun `not mine, not ready`() {
        val blog = blogger()
        val someoneElses = member().uploadPng()
        val prepared = blog.owner.post("/media/uploads", """{"contentType":"image/png","sizeBytes":10,"fileName":"a.png"}""")
        val unfinished = UploadedImage(UUID.fromString(prepared.field("mediaId")), prepared.field("imageUrl"))

        write(blog, body(someoneElses.url, unfinished.url))

        assertThat(postsOf(someoneElses)).isEmpty()
        assertThat(postsOf(unfinished)).isEmpty()
    }

    @Test
    @DisplayName("본문의 이미지가 20개를 넘으면 저장하지 않는다. 다른 사이트의 이미지도 센다")
    fun `twenty images at most`() {
        val blog = blogger()
        val image = blog.owner.uploadPng()
        val post = write(blog, body(image.url))
        val tooMany = body(*Array(21) { "https://elsewhere.test/$it.png" })

        val refused = blog.owner.post("/blogs/${blog.slug}/posts", draft(tooMany))
        assertThat(refused.statusCode()).isEqualTo(400)
        assertThat(refused.field("code")).isEqualTo("TOO_MANY_IMAGES")

        val revised = blog.owner.send("PUT", "/posts/$post", draft(tooMany))
        assertThat(revised.field("code")).isEqualTo("TOO_MANY_IMAGES")
        assertThat(blog.owner.get("/me/posts/$post").body()).contains(image.url)
        assertThat(postsOf(image)).containsExactly(post)

        assertThat(blog.owner.post("/blogs/${blog.slug}/posts", draft(body(*Array(20) { "https://elsewhere.test/$it.png" }))).statusCode())
            .isEqualTo(201)
    }

    private class Blog(val owner: TestBrowser, val slug: String)

    private fun member(): TestBrowser = TestBrowser(port).also { it.signUpAsNewMember(codes) }

    private fun blogger(): Blog {
        val owner = member()
        val slug = "blog-${UUID.randomUUID().toString().take(8)}"
        check(owner.post("/blogs", """{"slug":"$slug","title":"제목"}""").statusCode() == 201)
        return Blog(owner, slug)
    }

    /** Writes a draft with [body]; returns its id. */
    private fun write(blog: Blog, body: String): UUID {
        val written = blog.owner.post("/blogs/${blog.slug}/posts", draft(body))
        check(written.statusCode() == 201) { written.body() }
        return UUID.fromString(written.field("postId"))
    }

    private fun revise(blog: Blog, post: UUID, body: String) {
        check(blog.owner.send("PUT", "/posts/$post", draft(body)).statusCode() == 200)
    }

    private fun body(vararg imageUrls: String) = imageUrls.joinToString("\\n\\n") { "![그림]($it)" }

    private fun draft(body: String) = """{"title":"글 ${UUID.randomUUID().toString().take(8)}","body":"$body","categoryId":null}"""

    private fun postsOf(image: UploadedImage): List<UUID> {
        val row = ApiTestEnvironment.row(
            "select string_agg(post_id::text, ',') as posts from media.media_attachments where media_id = ?",
            image.id,
        )
        return (row?.get("posts") as String?)?.split(',')?.map(UUID::fromString).orEmpty()
    }

    private fun unattachedSince(image: UploadedImage): Any? =
        ApiTestEnvironment.row("select unattached_since from media.media where id = ?", image.id)!!["unattached_since"]
}
