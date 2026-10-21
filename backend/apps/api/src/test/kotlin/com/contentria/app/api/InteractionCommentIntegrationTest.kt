package com.contentria.app.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.http.HttpResponse
import java.util.UUID

/** Comments and replies (interaction 01-rules 흐름 3, 4). */
class InteractionCommentIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    private val anyone get() = TestBrowser(port)

    @Test
    @DisplayName("댓글과 답글을 쓰면 누구나 글 아래에서 보고, 쓸 때마다 CommentAdded를 아웃박스에 남긴다")
    fun `writing comments and replies`() {
        val blog = blogger()
        val post = publish(blog)
        val reader = TestBrowser(port)
        val readerId = reader.signUpAsNewMember(codes)

        val written = reader.post("/posts/$post/comments", """{"body":"  좋은 글이네요\n잘 읽었습니다  "}""")
        assertThat(written.statusCode()).isEqualTo(201)
        val comment = written.field("commentId")
        assertThat(written.body()).contains("\"body\":\"좋은 글이네요\\n잘 읽었습니다\"")

        // 자기 글에도 쓴다. 블로그 소유자가 답하는 것은 대화다
        val reply = blog.owner.post("/posts/$post/comments", """{"body":"감사합니다","parentId":"$comment"}""")
        assertThat(reply.statusCode()).isEqualTo(201)
        assertThat(reply.field("parentId")).isEqualTo(comment)

        val page = anyone.get("/posts/$post/comments")
        assertThat(page.statusCode()).isEqualTo(200)
        assertThat(ids(page)).containsExactly(comment, reply.field("commentId"))
        assertThat(page.body()).contains("\"commenterId\":\"$readerId\"", "\"nextCursor\":null")
        assertThat(anyone.get("/interactions/counts?postIds=$post").body()).contains("\"comments\":{\"$post\":2}")

        val event = ApiTestEnvironment.row(
            "select event_type, payload::text as payload from interaction.outbox_event where aggregate_id = ?",
            UUID.fromString(reply.field("commentId")),
        )!!
        assertThat(event["event_type"]).isEqualTo("CommentAdded")
        assertThat(event["payload"] as String).contains(
            "\"postId\": \"$post\"",
            "\"blogId\": \"${blog.id}\"",
            "\"blogOwnerId\": \"${blog.ownerId}\"",
            "\"parentCommenterId\": \"$readerId\"",
        )
    }

    @Test
    @DisplayName("답글은 한 단계이고, 같은 글의 댓글에만 단다. 발행되지 않은 글에는 쓸 수 없다")
    fun `writing refusals`() {
        val blog = blogger()
        val post = publish(blog)
        val otherPost = publish(blog)
        val draft = blog.owner.post("/blogs/${blog.slug}/posts", draft()).field("postId")
        val reader = member()
        val comment = reader.post("/posts/$post/comments", """{"body":"댓글"}""").field("commentId")
        val reply = reader.post("/posts/$post/comments", """{"body":"답글","parentId":"$comment"}""").field("commentId")

        assertThat(reader.post("/posts/$post/comments", """{"body":"답글의 답글","parentId":"$reply"}""").field("code")).isEqualTo("REPLY_TO_REPLY")
        assertThat(reader.post("/posts/$otherPost/comments", """{"body":"엉뚱한 답글","parentId":"$comment"}""").field("code")).isEqualTo("INVALID_PARENT")
        assertThat(reader.post("/posts/$post/comments", """{"body":"없는 부모","parentId":"${UUID.randomUUID()}"}""").field("code")).isEqualTo("INVALID_PARENT")
        assertThat(reader.post("/posts/$draft/comments", """{"body":"임시저장 글에"}""").statusCode()).isEqualTo(404)
        assertThat(reader.post("/posts/$post/comments", """{"body":"   "}""").field("code")).isEqualTo("INVALID_COMMENT")
        assertThat(reader.post("/posts/$post/comments", """{"body":"${"가".repeat(1001)}"}""").field("code")).isEqualTo("INVALID_COMMENT")
        assertThat(reader.post("/posts/$post/comments", """{}""").field("code")).isEqualTo("INVALID_REQUEST")
        assertThat(anyone.post("/posts/$post/comments", """{"body":"익명"}""").statusCode()).isEqualTo(401)
        assertThat(anyone.get("/posts/$draft/comments").statusCode()).isEqualTo(404)

        assertThat(commentsOn(post)).isEqualTo(2)
        assertThat(eventsAbout(post)).isEqualTo(2)
    }

    @Test
    @DisplayName("작성자만 고치고, 고친 댓글에는 수정됨이 붙는다. 숨긴 글의 댓글은 고칠 수 없다")
    fun `editing`() {
        val blog = blogger()
        val post = publish(blog)
        val reader = member()
        val comment = reader.post("/posts/$post/comments", """{"body":"처음 쓴 말"}""").field("commentId")

        val edited = reader.patch("/comments/$comment", """{"body":"고친 말"}""")
        assertThat(edited.statusCode()).isEqualTo(200)
        assertThat(edited.body()).contains("\"body\":\"고친 말\"", "\"edited\":true")

        val notMine = blog.owner.patch("/comments/$comment", """{"body":"남의 댓글"}""")
        assertThat(notMine.statusCode()).isEqualTo(403)
        assertThat(notMine.field("code")).isEqualTo("NOT_COMMENT_AUTHOR")
        assertThat(reader.patch("/comments/${UUID.randomUUID()}", """{"body":"없는 댓글"}""").field("code")).isEqualTo("COMMENT_NOT_FOUND")

        blog.owner.post("/posts/$post/hide", "")
        assertThat(reader.patch("/comments/$comment", """{"body":"숨긴 뒤"}""").field("code")).isEqualTo("POST_NOT_FOUND")
        assertThat(anyone.get("/posts/$post/comments").statusCode()).isEqualTo(404)
    }

    @Test
    @DisplayName("답글이 달린 댓글을 지우면 자리만 남고, 마지막 답글이 지워지면 자리도 사라진다")
    fun `deleting leaves a place while replies hang from it`() {
        val blog = blogger()
        val post = publish(blog)
        val writer = member()
        val replier = member()
        val alone = writer.post("/posts/$post/comments", """{"body":"답글 없는 댓글"}""").field("commentId")
        val comment = writer.post("/posts/$post/comments", """{"body":"답글 달릴 댓글"}""").field("commentId")
        val first = replier.post("/posts/$post/comments", """{"body":"첫 답글","parentId":"$comment"}""").field("commentId")
        val second = replier.post("/posts/$post/comments", """{"body":"둘째 답글","parentId":"$comment"}""").field("commentId")

        assertThat(replier.delete("/comments/$comment").field("code")).isEqualTo("NOT_COMMENT_AUTHOR")
        assertThat(writer.delete("/comments/$alone").statusCode()).isEqualTo(204)
        assertThat(writer.delete("/comments/$alone").statusCode()).isEqualTo(404)

        assertThat(writer.delete("/comments/$comment").statusCode()).isEqualTo(204)
        val page = anyone.get("/posts/$post/comments")
        assertThat(ids(page)).containsExactly(comment, first, second)
        assertThat(page.body()).contains(
            "\"commentId\":\"$comment\",\"parentId\":null,\"commenterId\":null,\"body\":\"\",\"edited\":false,\"removed\":true",
        )
        assertThat(anyone.get("/interactions/counts?postIds=$post").body()).contains("\"comments\":{\"$post\":2}")

        assertThat(writer.patch("/comments/$comment", """{"body":"되살리기"}""").field("code")).isEqualTo("COMMENT_REMOVED")
        assertThat(writer.delete("/comments/$comment").field("code")).isEqualTo("COMMENT_REMOVED")
        assertThat(replier.post("/posts/$post/comments", """{"body":"자리에 답글","parentId":"$comment"}""").field("code")).isEqualTo("COMMENT_REMOVED")

        assertThat(replier.delete("/comments/$first").statusCode()).isEqualTo(204)
        assertThat(ids(anyone.get("/posts/$post/comments"))).containsExactly(comment, second)
        assertThat(replier.delete("/comments/$second").statusCode()).isEqualTo(204)
        assertThat(anyone.get("/posts/$post/comments").body()).isEqualTo("""{"comments":[],"nextCursor":null}""")
        assertThat(commentsOn(post)).isEqualTo(0)
    }

    @Test
    @DisplayName("최상위 댓글을 20개씩 오래된 순으로 나누고, 같은 시각의 댓글도 빠지거나 겹치지 않는다")
    fun `pages of comments`() {
        val blog = blogger()
        val post = publish(blog)
        val reader = member()
        val all = (1..21).map { reader.post("/posts/$post/comments", """{"body":"댓글 $it"}""").field("commentId") }
        // 모두 같은 시각에 쓴 것으로 만들어 ID만으로 순서가 갈리게 한다
        ApiTestEnvironment.execute(
            "update interaction.comments set created_at = '2026-01-01T00:00:00Z' where post_id = ?",
            UUID.fromString(post),
        )

        val firstPage = anyone.get("/posts/$post/comments")
        assertThat(ids(firstPage)).hasSize(20)
        val cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(firstPage.body())!!.groupValues[1]

        val secondPage = anyone.get("/posts/$post/comments?cursor=$cursor")
        assertThat(ids(secondPage)).hasSize(1)
        assertThat(secondPage.body()).contains("\"nextCursor\":null")
        assertThat(ids(firstPage) + ids(secondPage)).containsExactlyInAnyOrderElementsOf(all).doesNotHaveDuplicates()
        assertThat(anyone.get("/posts/$post/comments?cursor=not-a-cursor").field("code")).isEqualTo("INVALID_CURSOR")
    }

    @Test
    @DisplayName("정지된 계정은 쓰거나 고칠 수 없지만, 자기 댓글을 지울 수는 있다")
    fun `a suspended account can only delete`() {
        val blog = blogger()
        val post = publish(blog)
        val reader = TestBrowser(port)
        val readerId = reader.signUpAsNewMember(codes)
        val comment = reader.post("/posts/$post/comments", """{"body":"정지 전에 쓴 댓글"}""").field("commentId")

        ApiTestEnvironment.execute("update member.accounts set status = 'SUSPENDED' where id = ?", readerId)

        assertThat(reader.post("/posts/$post/comments", """{"body":"정지 뒤"}""").field("code")).isEqualTo("ACCOUNT_NOT_ACTIVE")
        assertThat(reader.patch("/comments/$comment", """{"body":"정지 뒤"}""").statusCode()).isEqualTo(403)
        assertThat(reader.delete("/comments/$comment").statusCode()).isEqualTo(204)
        assertThat(commentsOn(post)).isEqualTo(0)
    }

    private class Blog(val owner: TestBrowser, val ownerId: UUID, val slug: String, val id: String)

    private fun member(): TestBrowser = TestBrowser(port).also { it.signUpAsNewMember(codes) }

    private fun blogger(): Blog {
        val owner = TestBrowser(port)
        val ownerId = owner.signUpAsNewMember(codes)
        val slug = "blog-${UUID.randomUUID().toString().take(8)}"
        val created = owner.post("/blogs", """{"slug":"$slug","title":"제목"}""")
        check(created.statusCode() == 201)
        return Blog(owner, ownerId, slug, created.field("blogId"))
    }

    /** Writes and publishes a post; returns its id. */
    private fun publish(blog: Blog): String {
        val id = blog.owner.post("/blogs/${blog.slug}/posts", draft()).field("postId")
        check(blog.owner.post("/posts/$id/publish", "").statusCode() == 200)
        return id
    }

    private fun draft() = """{"title":"글 ${UUID.randomUUID().toString().take(8)}","body":"본문","categoryId":null}"""

    private fun ids(page: HttpResponse<String>): List<String> =
        Regex("\"commentId\":\"([^\"]+)\"").findAll(page.body()).map { it.groupValues[1] }.toList()

    private fun commentsOn(postId: String): Long =
        ApiTestEnvironment.row("select count(*) as n from interaction.comments where post_id = ?", UUID.fromString(postId))!!["n"] as Long

    private fun eventsAbout(postId: String): Long =
        ApiTestEnvironment.row(
            "select count(*) as n from interaction.outbox_event where aggregate_type = 'comment' and payload ->> 'postId' = ?",
            postId,
        )!!["n"] as Long
}
