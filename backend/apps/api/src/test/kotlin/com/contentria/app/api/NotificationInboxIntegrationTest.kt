package com.contentria.app.api

import com.contentria.app.api.TestKafka.eventually
import com.contentria.notification.application.NotificationRetention
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.http.HttpResponse
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The notification inbox over HTTP (notification 01-rules 흐름 3) and its 90 days. */
class NotificationInboxIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Autowired
    private lateinit var retention: NotificationRetention

    @Test
    @DisplayName("독자가 댓글을 쓰면 블로그 소유자의 알림함에 ID만 담긴 알림이 생긴다")
    fun `a comment shows up in the owner's inbox`() {
        val owner = TestBrowser(port)
        owner.signUpAsNewMember(codes)
        val slug = "blog-${UUID.randomUUID().toString().take(8)}"
        val blogId = owner.post("/blogs", """{"slug":"$slug","title":"제목"}""").field("blogId")
        val post = owner.post("/blogs/$slug/posts", """{"title":"글","body":"본문","categoryId":null}""").field("postId")
        check(owner.post("/posts/$post/publish", "").statusCode() == 200)
        val reader = TestBrowser(port)
        val readerId = reader.signUpAsNewMember(codes)
        val comment = reader.post("/posts/$post/comments", """{"body":"잘 읽었습니다"}""").field("commentId")

        eventually { owner.get("/me/notifications/unread-count").body() == """{"count":1}""" }
        val page = owner.get("/me/notifications")

        assertThat(page.statusCode()).isEqualTo(200)
        assertThat(page.body()).contains(
            "\"kind\":\"COMMENT_ON_POST\"",
            "\"postId\":\"$post\"",
            "\"blogId\":\"$blogId\"",
            "\"commentId\":\"$comment\"",
            "\"actorId\":\"$readerId\"",
            "\"read\":false",
            "\"nextCursor\":null",
        )
        assertThat(ids(page)).hasSize(1)
        assertThat(reader.get("/me/notifications").body()).isEqualTo("""{"notifications":[],"nextCursor":null}""")
    }

    @Test
    @DisplayName("하나를 읽으면 안 읽은 수가 줄고, 다시 읽어도 성공한다. 남의 알림과 없는 알림은 같은 404다")
    fun `reading one`() {
        val (me, myId) = member()
        val (other, otherId) = member()
        val mine = insert(myId)
        insert(myId)
        val theirs = insert(otherId)

        assertThat(me.get("/me/notifications/unread-count").body()).isEqualTo("""{"count":2}""")
        assertThat(me.post("/me/notifications/$mine/read", "").statusCode()).isEqualTo(204)
        val firstRead = readAt(mine)
        assertThat(me.post("/me/notifications/$mine/read", "").statusCode()).isEqualTo(204)
        assertThat(readAt(mine)).isNotNull().isEqualTo(firstRead)
        assertThat(me.get("/me/notifications/unread-count").body()).isEqualTo("""{"count":1}""")
        assertThat(me.get("/me/notifications").body()).contains("\"notificationId\":\"$mine\"", "\"read\":true")

        val notMine = me.post("/me/notifications/$theirs/read", "")
        assertThat(notMine.statusCode()).isEqualTo(404)
        assertThat(notMine.field("code")).isEqualTo("NOTIFICATION_NOT_FOUND")
        assertThat(me.post("/me/notifications/${UUID.randomUUID()}/read", "").field("code")).isEqualTo("NOTIFICATION_NOT_FOUND")
        assertThat(me.post("/me/notifications/not-an-id/read", "").field("code")).isEqualTo("INVALID_REQUEST")
        assertThat(readAt(theirs)).isNull()
        assertThat(other.get("/me/notifications/unread-count").body()).isEqualTo("""{"count":1}""")
    }

    @Test
    @DisplayName("모두 읽음은 내 안 읽은 알림만 읽음으로 바꾼다. 로그인하지 않으면 알림함을 볼 수 없다")
    fun `reading all`() {
        val (me, myId) = member()
        val (other, otherId) = member()
        repeat(3) { insert(myId) }
        insert(otherId)

        assertThat(me.post("/me/notifications/read-all", "").statusCode()).isEqualTo(204)

        assertThat(me.get("/me/notifications/unread-count").body()).isEqualTo("""{"count":0}""")
        assertThat(other.get("/me/notifications/unread-count").body()).isEqualTo("""{"count":1}""")
        assertThat(TestBrowser(port).get("/me/notifications").statusCode()).isEqualTo(401)
    }

    @Test
    @DisplayName("최신순으로 20개씩 나누고, 같은 시각의 알림도 빠지거나 겹치지 않는다")
    fun `pages`() {
        val (me, myId) = member()
        val at = Instant.parse("2026-01-01T00:00:00Z")
        val all = (1..21).map { insert(myId, at) }
        val newest = insert(myId, at.plusSeconds(60))

        val firstPage = me.get("/me/notifications")
        assertThat(ids(firstPage)).hasSize(20).startsWith(newest)
        val cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(firstPage.body())!!.groupValues[1]

        val secondPage = me.get("/me/notifications?cursor=$cursor")
        assertThat(ids(secondPage)).hasSize(2)
        assertThat(secondPage.body()).contains("\"nextCursor\":null")
        assertThat(ids(firstPage) + ids(secondPage)).containsExactlyInAnyOrderElementsOf(all + newest).doesNotHaveDuplicates()
        assertThat(me.get("/me/notifications?cursor=not-a-cursor").field("code")).isEqualTo("INVALID_CURSOR")
    }

    @Test
    @DisplayName("만든 지 90일이 지난 알림은 읽었는지와 상관없이 지운다")
    fun `ninety days`() {
        val (_, myId) = member()
        val now = Instant.now()
        val old = insert(myId, now.minus(91, ChronoUnit.DAYS))
        val oldRead = insert(myId, now.minus(91, ChronoUnit.DAYS))
        ApiTestEnvironment.execute("update notification.notifications set read_at = now() where id = ?", oldRead)
        val recent = insert(myId, now.minus(89, ChronoUnit.DAYS))

        retention.removeExpired()

        assertThat(exists(old)).isFalse()
        assertThat(exists(oldRead)).isFalse()
        assertThat(exists(recent)).isTrue()
    }

    private fun member(): Pair<TestBrowser, UUID> {
        val browser = TestBrowser(port)
        return browser to browser.signUpAsNewMember(codes)
    }

    /** A new-post notification for [recipient], straight into the table. */
    private fun insert(recipient: UUID, at: Instant = Instant.now()): UUID {
        val id = UUID.randomUUID()
        ApiTestEnvironment.execute(
            "insert into notification.notifications (id, recipient_id, kind, post_id, blog_id, source_event_id, created_at) " +
                "values (?, ?, 'NEW_POST', ?, ?, ?, ?)",
            id, recipient, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(at),
        )
        return id
    }

    private fun readAt(id: UUID): Any? = ApiTestEnvironment.row("select read_at from notification.notifications where id = ?", id)!!["read_at"]

    private fun exists(id: UUID): Boolean = ApiTestEnvironment.row("select 1 as found from notification.notifications where id = ?", id) != null

    private fun ids(page: HttpResponse<String>): List<UUID> =
        Regex("\"notificationId\":\"([^\"]+)\"").findAll(page.body()).map { UUID.fromString(it.groupValues[1]) }.toList()
}
