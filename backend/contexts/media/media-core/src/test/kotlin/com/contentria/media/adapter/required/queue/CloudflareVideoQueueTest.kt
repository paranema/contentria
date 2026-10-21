package com.contentria.media.adapter.required.queue

import com.contentria.media.application.required.QueuedObject
import com.contentria.media.config.MediaWorkerProperties
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID

/** Against a stand-in for Cloudflare's REST API, answering as docs/operations/cloudflare-video-pipeline.md shows. */
class CloudflareVideoQueueTest {

    private val token = UUID.randomUUID().toString()
    private val requests = mutableListOf<Triple<String, String?, String>>()
    private var answer = 200 to "{}"
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            requests += Triple(exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization"), exchange.requestBody.readAllBytes().decodeToString())
            val (status, body) = answer
            exchange.sendResponseHeaders(status, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        start()
    }

    private val queue = CloudflareVideoQueue(
        MediaWorkerProperties.Queue(
            accountId = "acct", queueId = "q1", apiToken = token,
            baseUrl = URI.create("http://127.0.0.1:${server.address.port}/client/v4"),
        ),
    )

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    @DisplayName("받아 온 메시지에서 객체 키와 동작을 읽는다. 본문이 문자열로 와도 읽는다")
    fun `pulling`() {
        answer = 200 to """
            {"result":{"messages":[
              {"lease_id":"l1","body":{"object":{"key":"raw/a/original.mp4","size":10},"action":"PutObject"}},
              {"lease_id":"l2","body":"{\"object\":{\"key\":\"raw/b/original.mov\"},\"action\":\"CompleteMultipartUpload\"}"},
              {"body":{"object":{"key":"raw/c/original.mp4"},"action":"PutObject"}}
            ]}}
        """.trimIndent()

        val messages = queue.pull()

        assertThat(messages).containsExactly(
            QueuedObject("l1", "raw/a/original.mp4", "PutObject"),
            QueuedObject("l2", "raw/b/original.mov", "CompleteMultipartUpload"),
        )
        val (path, auth, body) = requests.single()
        assertThat(path).isEqualTo("/client/v4/accounts/acct/queues/q1/messages/pull")
        assertThat(auth).isEqualTo("Bearer $token")
        assertThat(body).contains("\"visibility_timeout_ms\":1800000", "\"batch_size\":1")
    }

    @Test
    @DisplayName("확인은 받은 영수증으로 보낸다")
    fun `acknowledging`() {
        queue.ack("l1")

        val (path, _, body) = requests.single()
        assertThat(path).isEqualTo("/client/v4/accounts/acct/queues/q1/messages/ack")
        assertThat(body).isEqualTo("""{"acks":[{"lease_id":"l1"}]}""")
    }

    @Test
    @DisplayName("2xx가 아니면 실패로 알리고, 토큰을 메시지에 담지 않는다")
    fun `failures`() {
        answer = 403 to """{"errors":[{"message":"forbidden"}]}"""

        assertThatThrownBy { queue.pull() }.hasMessageContaining("403").hasMessageNotContaining(token)
    }
}
