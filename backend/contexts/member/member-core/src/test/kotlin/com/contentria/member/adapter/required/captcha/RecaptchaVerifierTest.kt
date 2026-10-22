package com.contentria.member.adapter.required.captcha

import com.contentria.member.application.required.HumanVerifierUnavailable
import com.contentria.member.application.required.InvisibleAssessment
import com.contentria.member.config.MemberProperties
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/** Against a stand-in for Google's siteverify, answering as its documentation shows. */
class RecaptchaVerifierTest {

    private val v3Secret = UUID.randomUUID().toString()
    private val v2Secret = UUID.randomUUID().toString()
    private val forms = mutableListOf<Map<String, String>>()
    private var answer = 200 to "{}"
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/recaptcha/api/siteverify") { exchange ->
            forms += exchange.requestBody.readAllBytes().decodeToString().split('&').associate {
                val (key, value) = it.split('=', limit = 2)
                key to URLDecoder.decode(value, Charsets.UTF_8)
            }
            val (status, body) = answer
            exchange.sendResponseHeaders(status, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        start()
    }

    private val verifier = RecaptchaVerifier(
        MemberProperties(
            accessToken = MemberProperties.AccessToken(UUID.randomUUID().toString()),
            recaptcha = MemberProperties.Recaptcha(
                v3SecretKey = v3Secret,
                v2SecretKey = v2Secret,
                verifyUrl = URI.create("http://127.0.0.1:${server.address.port}/recaptcha/api/siteverify"),
            ),
        ),
    )

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    @DisplayName("보이지 않는 확인은 v3 비밀키로 묻고 점수와 동작을 읽는다")
    fun `invisible`() {
        answer = 200 to """{"success":true,"score":0.7,"action":"login","hostname":"localhost"}"""

        assertThat(verifier.assessInvisible("tok+en")).isEqualTo(InvisibleAssessment(0.7, "login"))
        assertThat(forms.single()).isEqualTo(mapOf("secret" to v3Secret, "response" to "tok+en"))
    }

    @Test
    @DisplayName("받아들여지지 않거나 점수가 없는 토큰은 null이다")
    fun `rejected invisible tokens`() {
        answer = 200 to """{"success":false,"error-codes":["invalid-input-response"]}"""
        assertThat(verifier.assessInvisible("t")).isNull()

        // A checkbox token sent as an invisible one: accepted, but with no score.
        answer = 200 to """{"success":true,"hostname":"localhost"}"""
        assertThat(verifier.assessInvisible("t")).isNull()
    }

    @Test
    @DisplayName("체크박스는 v2 비밀키로 묻는다")
    fun `checkbox`() {
        answer = 200 to """{"success":true}"""
        assertThat(verifier.acceptsCheckbox("t")).isTrue()
        assertThat(forms.single()["secret"]).isEqualTo(v2Secret)

        answer = 200 to """{"success":false}"""
        assertThat(verifier.acceptsCheckbox("t")).isFalse()
    }

    @Test
    @DisplayName("공급자가 2xx가 아니면 물을 수 없는 것으로 알리고, 비밀키를 메시지에 담지 않는다")
    fun `unavailable`() {
        answer = 500 to "oops"

        assertThatThrownBy { verifier.assessInvisible("t") }
            .isInstanceOf(HumanVerifierUnavailable::class.java)
            .hasMessageNotContaining(v3Secret)
    }
}
