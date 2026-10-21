package com.contentria.app.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.UUID

/** A post's attached video: preparing, attaching, and what readers get (media 01-rules 흐름 V1, V3, V4). */
class PostVideoIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Test
    @DisplayName("영상 업로드를 준비하면 대기 상태의 영상과 업로드 주소를 준다. 허용하지 않은 형식과 500MB 초과는 거부한다")
    fun `preparing a video`() {
        val member = member()

        val prepared = member.post("/media/videos/uploads", request("video/mp4", 1024, "clip.mp4"))

        assertThat(prepared.statusCode()).isEqualTo(201)
        assertThat(prepared.body()).contains("\"uploadUrl\":\"http", "\"expiresAt\"")
        val id = UUID.fromString(prepared.field("videoId"))
        assertThat(column(id, "status")).isEqualTo("PENDING")
        assertThat(column(id, "raw_key")).isEqualTo("raw/$id/original.mp4")
        assertThat(member.post("/media/videos/uploads", request("video/avi", 10, "a.avi")).field("code")).isEqualTo("UNSUPPORTED_VIDEO_FORMAT")
        assertThat(member.post("/media/videos/uploads", request("video/mp4", 524_288_001, "a.mp4")).field("code")).isEqualTo("VIDEO_TOO_LARGE")
        assertThat(TestBrowser(port).post("/media/videos/uploads", request("video/mp4", 10, "a.mp4")).statusCode()).isEqualTo(401)
    }

    @Test
    @DisplayName("글에 영상을 첨부하면 연결되고, 독자는 발행된 글에서 변환 중이면 상태만, 완료면 재생 주소를 받는다")
    fun `attaching and reading`() {
        val blog = blogger()
        val video = prepare(blog.owner)
        val post = write(blog, video)
        assertThat(postsOf(video)).containsExactly(post)
        assertThat(blog.owner.get("/me/posts/$post").field("videoId")).isEqualTo(video.toString())

        assertThat(TestBrowser(port).get("/posts/$post/video").statusCode()).isEqualTo(404)
        check(blog.owner.post("/posts/$post/publish", "").statusCode() == 200)

        val transcoding = TestBrowser(port).get("/posts/$post/video")
        assertThat(transcoding.statusCode()).isEqualTo(200)
        assertThat(transcoding.body()).contains("\"state\":\"TRANSCODING\"", "\"masterUrl\":null")

        ApiTestEnvironment.execute(
            "update media.videos set status = 'COMPLETED', master_key = ?, poster_key = ?, duration_ms = 12000, width = 1280, height = 720 where id = ?",
            "hls/$video/master.m3u8", "hls/$video/poster.jpg", video,
        )
        val completed = TestBrowser(port).get("/posts/$post/video")
        assertThat(completed.body()).contains(
            "\"state\":\"COMPLETED\"",
            "\"masterUrl\":\"${ApiTestEnvironment.mediaPublicBaseUrl}/hls/$video/master.m3u8\"",
            "\"posterUrl\":\"${ApiTestEnvironment.mediaPublicBaseUrl}/hls/$video/poster.jpg\"",
            "\"durationMs\":12000", "\"width\":1280", "\"height\":720",
        )

        blog.owner.post("/posts/$post/hide", "")
        assertThat(TestBrowser(port).get("/posts/$post/video").statusCode()).isEqualTo(404)
    }

    @Test
    @DisplayName("다른 영상으로 바꾸면 앞의 영상은 연결이 풀리고, 영상을 빼면 글에 영상이 없다")
    fun `replacing and removing`() {
        val blog = blogger()
        val first = prepare(blog.owner)
        val second = prepare(blog.owner)
        val post = write(blog, first)

        revise(blog, post, second)
        assertThat(postsOf(first)).isEmpty()
        assertThat(column(first, "unattached_since")).isNotNull()
        assertThat(postsOf(second)).containsExactly(post)

        revise(blog, post, null)
        assertThat(postsOf(second)).isEmpty()
        assertThat(blog.owner.get("/me/posts/$post").body()).contains("\"videoId\":null")
    }

    @Test
    @DisplayName("남이 올린 영상은 연결하지 않는다. 글을 지우면 영상의 연결이 풀린다")
    fun `someone else's video, and deleting the post`() {
        val blog = blogger()
        val someoneElses = prepare(member())
        val mine = prepare(blog.owner)

        val withTheirs = write(blog, someoneElses)
        assertThat(postsOf(someoneElses)).isEmpty()
        blog.owner.post("/posts/$withTheirs/publish", "")
        assertThat(TestBrowser(port).get("/posts/$withTheirs/video").statusCode()).isEqualTo(404)

        val withMine = write(blog, mine)
        assertThat(blog.owner.delete("/posts/$withMine").statusCode()).isEqualTo(204)
        assertThat(postsOf(mine)).isEmpty()
        assertThat(column(mine, "unattached_since")).isNotNull()
    }

    private class Blog(val owner: TestBrowser, val slug: String)

    private fun member(): TestBrowser = TestBrowser(port).also { it.signUpAsNewMember(codes) }

    private fun blogger(): Blog {
        val owner = member()
        val slug = "blog-${UUID.randomUUID().toString().take(8)}"
        check(owner.post("/blogs", """{"slug":"$slug","title":"제목"}""").statusCode() == 201)
        return Blog(owner, slug)
    }

    private fun request(contentType: String, size: Long, fileName: String) =
        """{"contentType":"$contentType","sizeBytes":$size,"fileName":"$fileName"}"""

    private fun prepare(browser: TestBrowser): UUID =
        UUID.fromString(browser.post("/media/videos/uploads", request("video/mp4", 1024, "clip.mp4")).field("videoId"))

    private fun draft(videoId: UUID?) =
        """{"title":"글 ${UUID.randomUUID().toString().take(8)}","body":"본문","categoryId":null,"videoId":${videoId?.let { "\"$it\"" } ?: "null"}}"""

    private fun write(blog: Blog, videoId: UUID?): UUID {
        val written = blog.owner.post("/blogs/${blog.slug}/posts", draft(videoId))
        check(written.statusCode() == 201) { written.body() }
        return UUID.fromString(written.field("postId"))
    }

    private fun revise(blog: Blog, post: UUID, videoId: UUID?) {
        check(blog.owner.send("PUT", "/posts/$post", draft(videoId)).statusCode() == 200)
    }

    private fun postsOf(video: UUID): List<UUID> {
        val row = ApiTestEnvironment.row("select string_agg(post_id::text, ',') as posts from media.video_attachments where video_id = ?", video)
        return (row?.get("posts") as String?)?.split(',')?.map(UUID::fromString).orEmpty()
    }

    private fun column(video: UUID, name: String): Any? = ApiTestEnvironment.row("select $name from media.videos where id = ?", video)!![name]
}
