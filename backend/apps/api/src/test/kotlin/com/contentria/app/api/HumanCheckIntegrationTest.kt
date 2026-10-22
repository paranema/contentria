package com.contentria.app.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.UUID

/** The human check before sign-up, login and mailing a code or link (member 01-rules, 사람 확인). */
class HumanCheckIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Test
    @DisplayName("토큰이 없거나 점수가 낮으면 403으로 체크박스를 요구하고, 코드를 보내지 않는다")
    fun `asks for the checkbox`() {
        val email = "human-${UUID.randomUUID()}@contentria.com"

        listOf("""{"email":"$email"}""", """{"email":"$email","recaptchaV3Token":"${FakeHumanVerifier.BOT}"}""").forEach { body ->
            val response = TestBrowser(port).post("/auth/sign-up/start", body)
            assertThat(response.statusCode()).isEqualTo(403)
            assertThat(response.field("code")).isEqualTo("RECAPTCHA_CHALLENGE_REQUIRED")
        }
        assertThat(codes.sentAnyTo(email)).isFalse()
    }

    @Test
    @DisplayName("체크박스 토큰이 받아들여지면 진행하고, 아니면 403 RECAPTCHA_FAILED다")
    fun `the checkbox`() {
        val email = "human-${UUID.randomUUID()}@contentria.com"

        val forged = TestBrowser(port).post("/auth/login/code/start", """{"email":"$email","recaptchaV2Token":"forged"}""")
        assertThat(forged.statusCode()).isEqualTo(403)
        assertThat(forged.field("code")).isEqualTo("RECAPTCHA_FAILED")

        val ticked = TestBrowser(port).post(
            "/auth/sign-up/start",
            """{"email":"$email","recaptchaV3Token":"${FakeHumanVerifier.BOT}","recaptchaV2Token":"${FakeHumanVerifier.HUMAN}"}""",
        )
        assertThat(ticked.statusCode()).isEqualTo(202)
        assertThat(codes.sentAnyTo(email)).isTrue()
    }

    @Test
    @DisplayName("비밀번호 로그인과 재설정 링크도 확인을 먼저 한다. 비밀번호는 맞춰 보지도 않는다")
    fun `login and reset link`() {
        val browser = TestBrowser(port)
        val email = "human-${UUID.randomUUID()}@contentria.com"
        browser.signUpAsNewMember(codes, email)

        val login = TestBrowser(port).post("/auth/login", """{"email":"$email","password":"wrong-password"}""")
        assertThat(login.statusCode()).isEqualTo(403)
        assertThat(login.field("code")).isEqualTo("RECAPTCHA_CHALLENGE_REQUIRED")
        assertThat(TestBrowser(port).post("/auth/password/reset-link", """{"email":"$email"}""").statusCode()).isEqualTo(403)
    }
}
