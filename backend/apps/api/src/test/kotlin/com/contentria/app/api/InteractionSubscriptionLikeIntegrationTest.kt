package com.contentria.app.api

import com.contentria.interaction.api.GetSubscribersQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

/** Subscriptions, likes, and the counts anyone may see (interaction 01-rules 흐름 1, 2). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RecordingSenders::class)
class InteractionSubscriptionLikeIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Autowired
    private lateinit var subscribers: GetSubscribersQuery

    private val anyone get() = TestBrowser(port)

    @Test
    @DisplayName("구독과 구독 취소는 몇 번을 보내도 같은 결과다")
    fun `subscribing is idempotent`() {
        val blog = blogger()
        val reader = member()

        assertThat(reader.put("/blogs/${blog.slug}/subscription").statusCode()).isEqualTo(204)
        assertThat(reader.put("/blogs/${blog.slug}/subscription").statusCode()).isEqualTo(204)
        assertThat(subscribers(blog.id)).isEqualTo(1)
        assertThat(reader.get("/me/subscriptions/${blog.slug}").body()).contains("\"subscribed\":true")

        val mine = reader.get("/me/subscriptions")
        assertThat(mine.body()).contains("\"blogId\":\"${blog.id}\"", "\"slug\":\"${blog.slug}\"", "\"subscribedAt\"")

        assertThat(reader.delete("/blogs/${blog.slug}/subscription").statusCode()).isEqualTo(204)
        assertThat(reader.delete("/blogs/${blog.slug}/subscription").statusCode()).isEqualTo(204)
        assertThat(subscribers(blog.id)).isEqualTo(0)
        assertThat(reader.get("/me/subscriptions/${blog.slug}").body()).contains("\"subscribed\":false")
        assertThat(reader.get("/me/subscriptions").body()).isEqualTo("[]")
    }

    @Test
    @DisplayName("내 블로그와 없는 블로그는 구독할 수 없다. 없는 블로그의 구독 취소는 그냥 성공한다")
    fun `subscription refusals`() {
        val blog = blogger()

        val own = blog.owner.put("/blogs/${blog.slug}/subscription")
        assertThat(own.statusCode()).isEqualTo(409)
        assertThat(own.field("code")).isEqualTo("OWN_BLOG")

        val reader = member()
        val missing = reader.put("/blogs/no-such-${UUID.randomUUID().toString().take(6)}/subscription")
        assertThat(missing.statusCode()).isEqualTo(404)
        assertThat(missing.field("code")).isEqualTo("BLOG_NOT_FOUND")
        assertThat(reader.delete("/blogs/no-such-blog/subscription").statusCode()).isEqualTo(204)
        assertThat(anyone.put("/blogs/${blog.slug}/subscription").statusCode()).isEqualTo(401)
    }

    @Test
    @DisplayName("좋아요는 발행된 글에만, 몇 번을 보내도 하나다")
    fun `liking is idempotent and only for published posts`() {
        val blog = blogger()
        val post = publish(blog, "좋아할 글")
        val draft = blog.owner.post("/blogs/${blog.slug}/posts", draft("임시저장 글")).field("postId")
        val reader = member()

        assertThat(reader.put("/posts/$post/like").statusCode()).isEqualTo(204)
        assertThat(reader.put("/posts/$post/like").statusCode()).isEqualTo(204)
        assertThat(likes(post)).isEqualTo(1)

        val missing = reader.put("/posts/$draft/like")
        assertThat(missing.statusCode()).isEqualTo(404)
        assertThat(missing.field("code")).isEqualTo("POST_NOT_FOUND")
        assertThat(reader.put("/posts/${UUID.randomUUID()}/like").statusCode()).isEqualTo(404)
        assertThat(reader.put("/posts/not-an-id/like").field("code")).isEqualTo("INVALID_REQUEST")

        val own = blog.owner.put("/posts/$post/like")
        assertThat(own.statusCode()).isEqualTo(409)
        assertThat(own.field("code")).isEqualTo("OWN_POST")

        assertThat(reader.get("/me/likes?postIds=$post,$draft").body()).isEqualTo("""{"postIds":["$post"]}""")

        assertThat(reader.delete("/posts/$post/like").statusCode()).isEqualTo(204)
        assertThat(reader.delete("/posts/$post/like").statusCode()).isEqualTo(204)
        assertThat(likes(post)).isEqualTo(0)
        assertThat(reader.get("/me/likes?postIds=$post").body()).isEqualTo("""{"postIds":[]}""")
    }

    @Test
    @DisplayName("숨긴 글에 이미 누른 좋아요는 취소할 수 있다")
    fun `unliking a hidden post`() {
        val blog = blogger()
        val post = publish(blog, "숨길 글")
        val reader = member()
        reader.put("/posts/$post/like")
        blog.owner.post("/posts/$post/hide", "")

        assertThat(reader.put("/posts/$post/like").statusCode()).isEqualTo(404)
        assertThat(reader.delete("/posts/$post/like").statusCode()).isEqualTo(204)
        assertThat(likes(post)).isEqualTo(0)
    }

    @Test
    @DisplayName("수는 로그인 없이 ID 묶음으로 보고, 아무도 누르지 않은 ID는 0이다")
    fun `counts in batches`() {
        val blog = blogger()
        val liked = publish(blog, "좋아요 받을 글")
        val quiet = publish(blog, "조용한 글")
        member().put("/posts/$liked/like")
        member().put("/posts/$liked/like")
        member().put("/blogs/${blog.slug}/subscription")
        val unknown = UUID.randomUUID()

        val counts = anyone.get("/interactions/counts?postIds=$liked,$quiet,$unknown&blogIds=${blog.id}")

        assertThat(counts.statusCode()).isEqualTo(200)
        assertThat(counts.body()).contains("\"$liked\":2", "\"$quiet\":0", "\"$unknown\":0", "\"${blog.id}\":1")
        assertThat(anyone.get("/interactions/counts").body()).isEqualTo("""{"likes":{},"comments":{},"subscribers":{}}""")

        val tooMany = (1..101).joinToString(",") { UUID.randomUUID().toString() }
        val refused = anyone.get("/interactions/counts?postIds=$tooMany")
        assertThat(refused.statusCode()).isEqualTo(400)
        assertThat(refused.field("code")).isEqualTo("TOO_MANY_IDS")
    }

    @Test
    @DisplayName("정지된 계정은 새로 구독하거나 좋아요를 누를 수 없지만, 취소는 할 수 있다")
    fun `a suspended account can only undo`() {
        val blog = blogger()
        val post = publish(blog, "정지 전에 누른 글")
        val other = blogger()
        val otherPost = publish(other, "새 글")
        val reader = TestBrowser(port)
        val readerId = reader.signUpAsNewMember(codes)
        reader.put("/blogs/${blog.slug}/subscription")
        reader.put("/posts/$post/like")

        ApiTestEnvironment.execute("update member.accounts set status = 'SUSPENDED' where id = ?", readerId)

        val subscribe = reader.put("/blogs/${other.slug}/subscription")
        assertThat(subscribe.statusCode()).isEqualTo(403)
        assertThat(subscribe.field("code")).isEqualTo("ACCOUNT_NOT_ACTIVE")
        assertThat(reader.put("/posts/$otherPost/like").statusCode()).isEqualTo(403)

        assertThat(reader.delete("/blogs/${blog.slug}/subscription").statusCode()).isEqualTo(204)
        assertThat(reader.delete("/posts/$post/like").statusCode()).isEqualTo(204)
        assertThat(subscribers(blog.id)).isEqualTo(0)
        assertThat(likes(post)).isEqualTo(0)
    }

    @Test
    @DisplayName("알림에는 블로그의 구독자 ID를 ID 순으로 1000개씩 나눠 알려준다. 해지한 회원과 다른 블로그의 구독자는 없다")
    fun `subscribers for notification`() {
        val blog = blogger()
        val reader = TestBrowser(port)
        val readerId = reader.signUpAsNewMember(codes)
        reader.put("/blogs/${blog.slug}/subscription")
        val leaver = member().also { it.put("/blogs/${blog.slug}/subscription") }
        leaver.delete("/blogs/${blog.slug}/subscription")
        blogger().let { other -> member().put("/blogs/${other.slug}/subscription") }
        // HTTP로 1000명을 만들지 않고 행을 직접 넣는다
        ApiTestEnvironment.execute(
            "insert into interaction.subscriptions (id, subscriber_id, blog_id, created_at, updated_at) " +
                "select gen_random_uuid(), gen_random_uuid(), ?, now(), now() from generate_series(1, 1000)",
            UUID.fromString(blog.id),
        )
        val blogId = UUID.fromString(blog.id)

        val first = subscribers.subscriberIds(blogId, null)
        val second = subscribers.subscriberIds(blogId, first.last())

        assertThat(first).hasSize(GetSubscribersQuery.PAGE_SIZE)
        assertThat(second).hasSize(1)
        val all = first + second
        assertThat(all).contains(readerId).doesNotHaveDuplicates().hasSize(1001)
        assertThat(all.map { it.toString() }).isSorted()
        assertThat(subscribers.subscriberIds(UUID.randomUUID(), null)).isEmpty()
    }

    private class Blog(val owner: TestBrowser, val slug: String, val id: String)

    private fun member(): TestBrowser = TestBrowser(port).also { it.signUpAsNewMember(codes) }

    private fun blogger(): Blog {
        val owner = member()
        val slug = "blog-${UUID.randomUUID().toString().take(8)}"
        val created = owner.post("/blogs", """{"slug":"$slug","title":"제목"}""")
        check(created.statusCode() == 201)
        return Blog(owner, slug, created.field("blogId"))
    }

    /** Writes and publishes a post; returns its id. */
    private fun publish(blog: Blog, title: String): String {
        val id = blog.owner.post("/blogs/${blog.slug}/posts", draft(title)).field("postId")
        check(blog.owner.post("/posts/$id/publish", "").statusCode() == 200)
        return id
    }

    private fun draft(title: String) = """{"title":"$title","body":"본문","categoryId":null}"""

    private fun TestBrowser.put(path: String) = send("PUT", path, "")

    private fun subscribers(blogId: String): Long =
        ApiTestEnvironment.row("select count(*) as n from interaction.subscriptions where blog_id = ?", UUID.fromString(blogId))!!["n"] as Long

    private fun likes(postId: String): Long =
        ApiTestEnvironment.row("select count(*) as n from interaction.likes where post_id = ?", UUID.fromString(postId))!!["n"] as Long

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = ApiTestEnvironment.register(registry)
    }
}
