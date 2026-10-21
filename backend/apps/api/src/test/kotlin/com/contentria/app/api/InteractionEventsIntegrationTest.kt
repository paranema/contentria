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

/**
 * Events between contexts through a real Kafka: the relay, the envelope, the inbox, and the dead
 * letters (docs/architecture/03-context-integration.md, 이벤트; interaction 02-implementation, 이벤트).
 */
class InteractionEventsIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Test
    @DisplayName("글을 지우면 PostDeleted가 Kafka를 거쳐 와서, 그 글의 댓글과 좋아요만 지운다")
    fun `a deleted post takes its comments and likes`() {
        val blog = blogger()
        val doomed = publish(blog)
        val kept = publish(blog)
        val reader = member()
        val comment = reader.post("/posts/$doomed/comments", """{"body":"사라질 댓글"}""").field("commentId")
        reader.post("/posts/$doomed/comments", """{"body":"사라질 답글","parentId":"$comment"}""")
        reader.send("PUT", "/posts/$doomed/like", "")
        reader.post("/posts/$kept/comments", """{"body":"남을 댓글"}""")
        reader.send("PUT", "/posts/$kept/like", "")

        assertThat(blog.owner.delete("/posts/$doomed").statusCode()).isEqualTo(204)

        eventually { comments(doomed) == 0L && likes(doomed) == 0L }
        assertThat(comments(kept)).isEqualTo(1)
        assertThat(likes(kept)).isEqualTo(1)
        val event = ApiTestEnvironment.row(
            "select id, published_at from publishing.outbox_event where aggregate_id = ? and event_type = 'PostDeleted'",
            UUID.fromString(doomed),
        )!!
        assertThat(event["published_at"]).isNotNull()
        assertThat(ApiTestEnvironment.row("select consumer from interaction.inbox_event where event_id = ?", event["id"]!!)!!["consumer"])
            .isEqualTo("interaction.post-deleted")
    }

    @Test
    @DisplayName("같은 PostDeleted가 다시 와도 인박스가 걸러 처리하지 않는다")
    fun `a repeated event is dropped`() {
        val blog = blogger()
        val post = publish(blog)
        blog.owner.delete("/posts/$post")
        val delivered = read("publishing.post") { it.key() == post && EventEnvelope.parse(it.value()).type == "PostDeleted" }
        eventually { inboxed(EventEnvelope.parse(delivered).eventId) }

        // 처리했다면 지웠을 행을 직접 넣고, 같은 메시지를 다시 보낸다
        ApiTestEnvironment.execute(
            "insert into interaction.likes (id, account_id, post_id, blog_id, created_at, updated_at) values (?, ?, ?, ?, now(), now())",
            UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(post), UUID.fromString(blog.id),
        )
        produce("publishing.post", post, delivered)

        // 토픽의 파티션이 하나라, 뒤에 보낸 이벤트가 처리되면 앞의 것도 처리된 것이다
        val marker = publish(blog)
        member().send("PUT", "/posts/$marker/like", "")
        blog.owner.delete("/posts/$marker")
        eventually { likes(marker) == 0L }
        assertThat(likes(post)).isEqualTo(1)
    }

    @Test
    @DisplayName("봉투가 아닌 메시지는 다시 시도하지 않고 DLT로 보내고, 뒤의 이벤트는 막히지 않는다")
    fun `a broken message goes to the dead letters`() {
        val key = "broken-${UUID.randomUUID()}"
        produce("publishing.post", key, "not an envelope")

        assertThat(read("publishing.post-dlt") { it.key() == key }).isEqualTo("not an envelope")

        val blog = blogger()
        val post = publish(blog)
        member().send("PUT", "/posts/$post/like", "")
        blog.owner.delete("/posts/$post")
        eventually { likes(post) == 0L }
    }

    @Test
    @DisplayName("댓글을 쓰면 CommentAdded가 interaction.comment에 댓글 ID를 키로 실린다")
    fun `comment added reaches its topic`() {
        val blog = blogger()
        val post = publish(blog)
        val reader = TestBrowser(port)
        val readerId = reader.signUpAsNewMember(codes)
        val comment = reader.post("/posts/$post/comments", """{"body":"알림이 갈 댓글"}""").field("commentId")

        val envelope = EventEnvelope.parse(read("interaction.comment") { it.key() == comment })

        assertThat(envelope.type).isEqualTo("CommentAdded")
        assertThat(envelope.version).isEqualTo(1)
        assertThat(envelope.aggregateId.toString()).isEqualTo(comment)
        assertThat(envelope.payload["blogOwnerId"].asString()).isEqualTo(blog.ownerId.toString())
        assertThat(envelope.payload["commenterId"].asString()).isEqualTo(readerId.toString())
        assertThat(envelope.payload["parentCommenterId"].isNull).isTrue()
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
        val draft = """{"title":"글 ${UUID.randomUUID().toString().take(8)}","body":"본문","categoryId":null}"""
        val id = blog.owner.post("/blogs/${blog.slug}/posts", draft).field("postId")
        check(blog.owner.post("/posts/$id/publish", "").statusCode() == 200)
        return id
    }

    private fun comments(postId: String): Long =
        ApiTestEnvironment.row("select count(*) as n from interaction.comments where post_id = ?", UUID.fromString(postId))!!["n"] as Long

    private fun likes(postId: String): Long =
        ApiTestEnvironment.row("select count(*) as n from interaction.likes where post_id = ?", UUID.fromString(postId))!!["n"] as Long

    private fun inboxed(eventId: UUID): Boolean =
        ApiTestEnvironment.row("select 1 as found from interaction.inbox_event where event_id = ?", eventId) != null
}
