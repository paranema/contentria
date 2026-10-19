package com.contentria.app.api

import com.contentria.member.application.required.VerificationCodeSender
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The member flows of 5a end to end: HTTP in, PostgreSQL behind, cookies carried by the client
 * the way a browser would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberFlowIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Test
    @DisplayName("이메일 가입, 프로필, 로그인, 토큰 교체가 끝까지 동작한다")
    fun `sign-up, profile, login and refresh`() {
        val browser = Browser()
        val email = "reader-${UUID.randomUUID()}@contentria.com"
        val password = "correct horse battery"

        // 가입 시작: 계정은 아직 없다
        val started = browser.post("/auth/sign-up/start", """{"email":"${email.uppercase()}"}""")
        assertThat(started.statusCode()).isEqualTo(202)
        val attemptId = started.field("attemptId")
        val code = codes.lastCodeFor(email)

        // 틀린 코드는 거부하고 실패 횟수를 남긴다
        val wrong = browser.post("/auth/sign-up/complete", completion(attemptId, wrongCodeFor(code), password))
        assertThat(wrong.statusCode()).isEqualTo(400)
        assertThat(wrong.body()).contains("VERIFICATION_MISMATCH")

        // 맞는 코드로 계정, 프로필, 세션이 생긴다
        val completed = browser.post("/auth/sign-up/complete", completion(attemptId, code, password))
        assertThat(completed.statusCode()).isEqualTo(201)
        assertThat(browser.cookie("accessToken")).isNotNull()
        assertThat(browser.cookie("refreshToken")).isNotNull()

        // 같은 시도는 다시 쓸 수 없다
        val reused = browser.post("/auth/sign-up/complete", completion(attemptId, code, password))
        assertThat(reused.statusCode()).isEqualTo(400)

        // 내 프로필: 자동 생성된 표시 이름과 정규화된 이메일
        val profile = browser.get("/me/profile")
        assertThat(profile.statusCode()).isEqualTo(200)
        assertThat(profile.field("email")).isEqualTo(email)
        assertThat(profile.field("displayName")).matches(".+_.+_\\d{4}")

        val renamed = browser.patch("/me/profile", """{"displayName":"  새 이름 ${UUID.randomUUID().toString().take(8)}  "}""")
        assertThat(renamed.statusCode()).isEqualTo(200)
        assertThat(renamed.field("displayName")).startsWith("새 이름 ")

        // 같은 주소로 다시 가입하면 409와 가입 수단을 알려준다
        val duplicate = browser.post("/auth/sign-up/start", """{"email":"$email"}""")
        assertThat(duplicate.statusCode()).isEqualTo(409)
        assertThat(duplicate.body()).contains("EMAIL_ALREADY_REGISTERED", "EMAIL")

        // 로그인: 틀린 비밀번호와 없는 주소는 같은 응답
        val other = Browser()
        assertThat(other.post("/auth/login", login(email, "wrong password")).statusCode()).isEqualTo(401)
        assertThat(other.post("/auth/login", login("nobody-${UUID.randomUUID()}@contentria.com", password)).statusCode()).isEqualTo(401)
        assertThat(other.post("/auth/login", login(email, password)).statusCode()).isEqualTo(200)
        assertThat(other.get("/me/profile").statusCode()).isEqualTo(200)

        // 토큰 교체: 옛 리프레시 토큰은 다시 쓸 수 없다
        val oldRefresh = other.cookie("refreshToken")!!
        assertThat(other.post("/auth/refresh", "").statusCode()).isEqualTo(200)
        assertThat(other.cookie("refreshToken")).isNotEqualTo(oldRefresh)
        assertThat(Browser().postWithCookie("/auth/refresh", "refreshToken=$oldRefresh").statusCode()).isEqualTo(401)

        // 토큰 없이는 내 프로필을 볼 수 없다
        assertThat(Browser().get("/me/profile").statusCode()).isEqualTo(401)
    }

    private fun completion(attemptId: String, code: String, password: String) =
        """{"attemptId":"$attemptId","code":"$code","password":"$password"}"""

    private fun login(email: String, password: String) = """{"email":"$email","password":"$password"}"""

    private fun wrongCodeFor(code: String) = if (code == "000000") "111111" else "000000"

    private fun HttpResponse<String>.field(name: String): String =
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(body())?.groupValues?.get(1)
            ?: error("No $name in ${body()}")

    /** A client that keeps cookies between requests, like a browser tab. */
    private inner class Browser {
        private val cookies = CookieManager()
        private val client = HttpClient.newBuilder().cookieHandler(cookies).build()

        fun get(path: String) = send(request(path).GET())
        fun post(path: String, json: String) = send(request(path).POST(HttpRequest.BodyPublishers.ofString(json)))
        fun patch(path: String, json: String) = send(request(path).method("PATCH", HttpRequest.BodyPublishers.ofString(json)))
        fun postWithCookie(path: String, cookie: String) =
            send(request(path).header("Cookie", cookie).POST(HttpRequest.BodyPublishers.noBody()))

        fun cookie(name: String): String? = cookies.cookieStore.cookies.firstOrNull { it.name == name }?.value

        private fun request(path: String) =
            HttpRequest.newBuilder(URI.create("http://localhost:$port/api$path")).header("Content-Type", "application/json")

        private fun send(builder: HttpRequest.Builder): HttpResponse<String> =
            client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    /** Captures codes instead of mailing them. */
    class RecordingCodeSender : VerificationCodeSender {
        private val codes = ConcurrentHashMap<String, String>()
        override fun send(email: String, code: String) { codes[email] = code }
        fun lastCodeFor(email: String): String = codes[email] ?: error("No code was sent to $email")
    }

    @TestConfiguration
    class Codes {
        @Bean
        @Primary
        fun recordingCodeSender() = RecordingCodeSender()
    }

    companion object {
        private val postgres = PostgreSQLContainer("postgres:17").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .schemas("member")
                .defaultSchema("member")
                .locations("classpath:db/migration/member")
                .load()
                .migrate()

            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("contentria.member.access-token.secret") {
                Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
            }
            registry.add("contentria.member.cookie.secure") { false }
            registry.add("contentria.member.mail.mode") { "log" }
        }
    }
}
