package com.contentria.app.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.UUID

/** Members' public profiles, for the screens that show who wrote something (member 01-rules 흐름 5). */
class PublicProfileIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Test
    @DisplayName("로그인하지 않아도 여러 회원의 표시 이름과 사진을 한 번에 준다. 이메일은 담지 않고, 없는 ID는 빠진다")
    fun `profiles of several members`() {
        val emailA = "public-a-${UUID.randomUUID()}@contentria.com"
        val a = TestBrowser(port).let { it.signUpAsNewMember(codes, emailA) to it.get("/me/profile").field("displayName") }
        val b = TestBrowser(port).let { it.signUpAsNewMember(codes) to it.get("/me/profile").field("displayName") }

        val response = TestBrowser(port).get("/members/profiles?ids=${a.first},${b.first},${UUID.randomUUID()}")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains(
            "\"accountId\":\"${a.first}\"", "\"displayName\":\"${a.second}\"",
            "\"accountId\":\"${b.first}\"", "\"displayName\":\"${b.second}\"",
            "\"pictureUrl\":null",
        ).doesNotContain(emailA, "email", "status")
        assertThat(Regex("\"accountId\"").findAll(response.body()).count()).isEqualTo(2)
    }

    @Test
    @DisplayName("한 번에 100명까지만 묻는다")
    fun `at most a hundred`() {
        val ids = (1..101).joinToString(",") { UUID.randomUUID().toString() }

        val response = TestBrowser(port).get("/members/profiles?ids=$ids")

        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(response.field("code")).isEqualTo("INVALID_REQUEST")
    }
}
