package com.contentria.app.api

import com.contentria.app.api.TestKafka.eventually
import com.contentria.app.api.TestKafka.produce
import com.contentria.app.api.TestKafka.read
import com.contentria.platform.messaging.EventEnvelope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.UUID

/** Posts and comments becoming notifications through Kafka (notification 01-rules 흐름 1, 2). */
class NotificationEventsIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Test
    @DisplayName("글을 처음 발행하면 그때의 구독자마다 새 글 알림이 생긴다. 구독하지 않은 회원과 소유자는 받지 않는다")
    fun `a new post reaches the subscribers`() {
        val blog = blogger()
        val first = member()
        val second = member()
        val stranger = member()
        first.browser.put("/blogs/${blog.slug}/subscription")
        second.browser.put("/blogs/${blog.slug}/subscription")

        val post = publish(blog)

        eventually { notificationsOn(post).size == 2 }
        assertThat(notificationsOn(post)).containsExactlyInAnyOrder("${first.id}:NEW_POST", "${second.id}:NEW_POST")
        assertThat(notificationsOn(post)).noneMatch { it.startsWith("${stranger.id}:") || it.startsWith("${blog.ownerId}:") }
        val fields = ApiTestEnvironment.row("select comment_id, actor_id from notification.notifications where post_id = ? limit 1", UUID.fromString(post))!!
        assertThat(fields.values).containsOnlyNulls()
    }

    @Test
    @DisplayName("비공개로 돌렸다 다시 공개해도, 같은 PostPublished가 다시 와도 알림은 늘지 않는다")
    fun `no second new-post notification`() {
        val blog = blogger()
        member().browser.put("/blogs/${blog.slug}/subscription")
        val post = publish(blog)
        eventually { notificationsOn(post).size == 1 }

        blog.owner.post("/posts/$post/hide", "")
        check(blog.owner.post("/posts/$post/republish", "").statusCode() == 200)
        produce("publishing.post", post, read("publishing.post") { it.key() == post && EventEnvelope.parse(it.value()).type == "PostPublished" })

        // 토픽의 파티션이 하나라, 뒤에 발행한 글의 알림이 생기면 앞의 메시지도 처리된 것이다
        val marker = publish(blog)
        eventually { notificationsOn(marker).size == 1 }
        assertThat(notificationsOn(post)).hasSize(1)
    }

    @Test
    @DisplayName("댓글은 블로그 소유자에게, 답글은 부모 댓글을 쓴 사람과 블로그 소유자에게 간다. 자기 댓글은 알리지 않는다")
    fun `comments and replies`() {
        val blog = blogger()
        val post = publish(blog)
        val reader = member()
        val replier = member()

        val own = blog.owner.post("/posts/$post/comments", """{"body":"소유자의 댓글"}""").field("commentId")
        val comment = reader.browser.post("/posts/$post/comments", """{"body":"독자의 댓글"}""").field("commentId")
        val reply = replier.browser.post("/posts/$post/comments", """{"body":"답글","parentId":"$comment"}""").field("commentId")

        // 쓴 순서대로 처리되므로, 마지막 답글의 알림이 생기면 앞의 둘도 처리된 것이다
        eventually { notificationsAbout(reply).size == 2 }
        assertThat(notificationsAbout(comment)).containsExactly("${blog.ownerId}:COMMENT_ON_POST")
        assertThat(notificationsAbout(reply)).containsExactlyInAnyOrder("${reader.id}:REPLY_TO_COMMENT", "${blog.ownerId}:COMMENT_ON_POST")
        assertThat(notificationsAbout(own)).isEmpty()
        val actor = ApiTestEnvironment.row("select actor_id from notification.notifications where comment_id = ? limit 1", UUID.fromString(reply))!!
        assertThat(actor["actor_id"]).isEqualTo(replier.id)
    }

    private class Blog(val owner: TestBrowser, val ownerId: UUID, val slug: String)

    private class Member(val browser: TestBrowser, val id: UUID)

    private fun member(): Member {
        val browser = TestBrowser(port)
        return Member(browser, browser.signUpAsNewMember(codes))
    }

    private fun blogger(): Blog {
        val owner = member()
        val slug = "blog-${UUID.randomUUID().toString().take(8)}"
        check(owner.browser.post("/blogs", """{"slug":"$slug","title":"제목"}""").statusCode() == 201)
        return Blog(owner.browser, owner.id, slug)
    }

    /** Writes and publishes a post; returns its id. */
    private fun publish(blog: Blog): String {
        val draft = """{"title":"글 ${UUID.randomUUID().toString().take(8)}","body":"본문","categoryId":null}"""
        val id = blog.owner.post("/blogs/${blog.slug}/posts", draft).field("postId")
        check(blog.owner.post("/posts/$id/publish", "").statusCode() == 200)
        return id
    }

    private fun TestBrowser.put(path: String) = send("PUT", path, "")

    /** "recipient:kind" for each notification about [postId]. */
    private fun notificationsOn(postId: String): List<String> = listed("post_id", postId)

    /** "recipient:kind" for each notification about [commentId]. */
    private fun notificationsAbout(commentId: String): List<String> = listed("comment_id", commentId)

    private fun listed(column: String, id: String): List<String> {
        val row = ApiTestEnvironment.row(
            "select string_agg(recipient_id::text || ':' || kind, ',') as listed from notification.notifications where $column = ?",
            UUID.fromString(id),
        )
        return (row?.get("listed") as String?)?.split(',').orEmpty()
    }
}
