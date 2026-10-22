package com.contentria.app.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort

/** How a request's session decides its answer (docs/conventions/security.md). */
class RequestAuthenticationIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Test
    @DisplayName("로그인한 요청이 없는 경로로 가면 404다. 401이면 화면은 세션이 끝난 줄 안다")
    fun `a signed-in request to nowhere is a 404`() {
        val member = TestBrowser(port).also { it.signUpAsNewMember(codes) }

        assertThat(member.get("/no-such-path").statusCode()).isEqualTo(404)
        assertThat(member.get("/me/profile").statusCode()).isEqualTo(200)
    }

    @Test
    @DisplayName("로그인하지 않은 요청은 없는 경로든 있는 경로든 공개가 아니면 401이다")
    fun `an anonymous request is a 401 wherever it goes`() {
        assertThat(TestBrowser(port).get("/no-such-path").statusCode()).isEqualTo(401)
        assertThat(TestBrowser(port).get("/me/profile").statusCode()).isEqualTo(401)
    }
}
