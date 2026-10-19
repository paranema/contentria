package com.contentria.app.api

import com.contentria.app.api.FakeOidcProvider.Identity
import com.contentria.member.application.required.AccountNotice
import com.contentria.member.domain.account.AuthProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpResponse
import java.util.Base64
import java.util.UUID

/**
 * Social sign-in and linking through the real Spring Security flow: the redirect with state,
 * nonce and PKCE, the signed authorization-request cookie, the code exchange, and RS256 ID token
 * verification against a key set. Only the provider is fake (docs/contexts/member/01-rules.md,
 * 흐름 2, 3, 4의 연동 해제).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RecordingSenders::class)
class SocialLoginIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Autowired
    private lateinit var notices: RecordingNoticeSender

    private val google = ApiTestEnvironment.google

    @Test
    @DisplayName("처음 보는 신원은 가입하고, 같은 신원으로 다시 오면 같은 계정으로 로그인한다")
    fun `social sign-up, then sign-in`() {
        val identity = verifiedIdentity()
        val browser = TestBrowser(port)

        val callback = socialSignIn(browser, identity)
        assertThat(callback.statusCode()).isEqualTo(302)
        assertThat(callback.location()).isEqualTo(ApiTestEnvironment.LOGIN_SUCCESS_URL)
        assertThat(browser.cookie("accessToken")).isNotNull()

        val profile = browser.get("/me/profile")
        assertThat(profile.field("email")).isEqualTo(identity.email)
        assertThat(profile.field("displayName")).matches(".+_.+_\\d{4}")
        assertThat(browser.get("/me/credentials").body()).isEqualTo("""{"providers":["GOOGLE"]}""")

        // PKCE: the client proved it started the request
        assertThat(google.tokenRequests.last()["code_verifier"]).isNotBlank()

        val again = TestBrowser(port)
        socialSignIn(again, identity)
        assertThat(again.get("/me/profile").field("accountId")).isEqualTo(profile.field("accountId"))
    }

    @Test
    @DisplayName("공급자가 확인하지 않은 주소나 주소가 없는 신원은 계정을 만들지 않는다")
    fun `unverified or missing address is refused`() {
        val unverified = TestBrowser(port)
        val refused = socialSignIn(unverified, verifiedIdentity().copy(emailVerified = null))
        assertThat(refused.location()).isEqualTo("${ApiTestEnvironment.LOGIN_FAILURE_URL}?error=SOCIAL_EMAIL_NOT_VERIFIED")
        assertThat(unverified.cookie("accessToken")).isNull()

        val noEmail = socialSignIn(TestBrowser(port), Identity("sub-${UUID.randomUUID()}", email = null, emailVerified = null))
        assertThat(noEmail.location()).isEqualTo("${ApiTestEnvironment.LOGIN_FAILURE_URL}?error=SOCIAL_EMAIL_MISSING")
    }

    @Test
    @DisplayName("같은 주소의 계정이 있으면 자동으로 연동하고 안내 메일을 보낸다")
    fun `automatic linking to an email account`() {
        val email = "member-${UUID.randomUUID()}@contentria.com"
        val accountId = signUpWithEmail(email)

        val browser = TestBrowser(port)
        val callback = socialSignIn(browser, Identity("sub-${UUID.randomUUID()}", email.uppercase(), emailVerified = true))

        assertThat(callback.location()).isEqualTo(ApiTestEnvironment.LOGIN_SUCCESS_URL)
        assertThat(browser.get("/me/profile").field("accountId")).isEqualTo(accountId)
        assertThat(browser.get("/me/credentials").body()).isEqualTo("""{"providers":["EMAIL","GOOGLE"]}""")
        assertThat(notices.sentTo(email)).containsExactly(AccountNotice.SocialProviderLinked(AuthProvider.GOOGLE, automatic = true))
    }

    @Test
    @DisplayName("로그인한 상태에서 연동하면 주소가 달라도 연결되고, 해제하면 그 신원으로는 들어올 수 없다")
    fun `manual link and unlink`() {
        val email = "member-${UUID.randomUUID()}@contentria.com"
        val browser = TestBrowser(port)
        val accountId = signUpWithEmail(email, browser)
        val identity = verifiedIdentity()

        val linked = socialSignIn(browser, identity, intent = "link")
        assertThat(linked.location()).isEqualTo("${ApiTestEnvironment.LINK_RESULT_URL}?linked=true")
        assertThat(browser.get("/me/credentials").body()).isEqualTo("""{"providers":["EMAIL","GOOGLE"]}""")
        assertThat(browser.get("/me/profile").field("email")).isEqualTo(email)
        assertThat(notices.sentTo(email)).containsExactly(AccountNotice.SocialProviderLinked(AuthProvider.GOOGLE, automatic = false))

        val viaGoogle = TestBrowser(port)
        socialSignIn(viaGoogle, identity)
        assertThat(viaGoogle.get("/me/profile").field("accountId")).isEqualTo(accountId)

        assertThat(browser.delete("/me/credentials/google").statusCode()).isEqualTo(204)
        assertThat(browser.get("/me/credentials").body()).isEqualTo("""{"providers":["EMAIL"]}""")
        assertThat(notices.sentTo(email)).endsWith(AccountNotice.SocialProviderUnlinked(AuthProvider.GOOGLE))
        assertThat(browser.delete("/me/credentials/google").body()).contains("PROVIDER_NOT_LINKED")

        // 해제된 신원은 이제 새 계정으로 가입한다. 주소가 다르기 때문이다.
        val afterUnlink = TestBrowser(port)
        socialSignIn(afterUnlink, identity)
        assertThat(afterUnlink.get("/me/profile").field("accountId")).isNotEqualTo(accountId)
    }

    @Test
    @DisplayName("다른 계정이 쓰는 신원은 연동하지 않고, 로그인하지 않은 연동 요청은 거부한다")
    fun `link refusals`() {
        val taken = verifiedIdentity()
        socialSignIn(TestBrowser(port), taken)

        val browser = TestBrowser(port)
        signUpWithEmail("member-${UUID.randomUUID()}@contentria.com", browser)
        assertThat(socialSignIn(browser, taken, intent = "link").location())
            .isEqualTo("${ApiTestEnvironment.LINK_RESULT_URL}?error=SOCIAL_IDENTITY_TAKEN")

        val anonymous = TestBrowser(port)
        assertThat(socialSignIn(anonymous, verifiedIdentity(), intent = "link").location())
            .isEqualTo("${ApiTestEnvironment.LINK_RESULT_URL}?error=SIGN_IN_REQUIRED")
        assertThat(anonymous.cookie("accessToken")).isNull()
    }

    @Test
    @DisplayName("소셜 전용 계정은 마지막 자격증명을 해제할 수 없다")
    fun `the last credential stays`() {
        val browser = TestBrowser(port)
        socialSignIn(browser, verifiedIdentity())

        val response = browser.delete("/me/credentials/google")
        assertThat(response.statusCode()).isEqualTo(409)
        assertThat(response.body()).contains("LAST_CREDENTIAL")
        assertThat(browser.delete("/me/credentials/email").statusCode()).isEqualTo(404)
    }

    @Test
    @DisplayName("공급자의 키로 서명되지 않은 ID 토큰은 거부한다")
    fun `an ID token with a forged signature is refused`() {
        val browser = TestBrowser(port)
        val identity = verifiedIdentity()

        val callback = socialSignIn(browser, identity, forged = true)

        assertThat(callback.location()).isEqualTo("${ApiTestEnvironment.LOGIN_FAILURE_URL}?error=SOCIAL_LOGIN_FAILED")
        assertThat(browser.cookie("accessToken")).isNull()
        // 같은 신원이 정상 토큰으로 오면 그때 처음 가입한다. 위조 토큰으로는 아무것도 만들어지지 않았다.
        assertThat(socialSignIn(browser, identity).location()).isEqualTo(ApiTestEnvironment.LOGIN_SUCCESS_URL)
    }

    /**
     * The attack the signature exists for: signed in as themselves, an attacker starts a link and
     * rewrites the cookie so that their own Google identity lands on the victim's account.
     */
    @Test
    @DisplayName("인가 요청 쿠키의 연동 대상 계정을 바꾸면 콜백이 거부한다")
    fun `a rewritten linking account is refused`() {
        val victimEmail = "victim-${UUID.randomUUID()}@contentria.com"
        val victim = TestBrowser(port)
        val victimId = signUpWithEmail(victimEmail, victim)

        val attacker = TestBrowser(port)
        val attackerId = signUpWithEmail("attacker-${UUID.randomUUID()}@contentria.com", attacker)

        val callback = socialSignIn(attacker, verifiedIdentity(), intent = "link") {
            attacker.rewriteCookie("oauth2AuthRequest") { value ->
                val (payload, signature) = value.split('.')
                val json = Base64.getUrlDecoder().decode(payload).decodeToString()
                check(json.contains(attackerId)) { "The cookie should name the linking account" }
                val rewritten = Base64.getUrlEncoder().withoutPadding().encodeToString(json.replace(attackerId, victimId).toByteArray())
                "$rewritten.$signature"
            }
        }

        // A request whose signature fails says nothing trustworthy, not even that it was a link.
        assertThat(callback.location()).isEqualTo("${ApiTestEnvironment.LOGIN_FAILURE_URL}?error=SOCIAL_LOGIN_FAILED")
        assertThat(victim.get("/me/credentials").body()).isEqualTo("""{"providers":["EMAIL"]}""")
        assertThat(notices.sentTo(victimEmail)).isEmpty()
    }

    /**
     * Plays the browser's part: follows the API's redirect to the provider, has the provider issue
     * a code for [identity], and returns with it to the callback.
     */
    private fun socialSignIn(
        browser: TestBrowser,
        identity: Identity,
        intent: String? = null,
        forged: Boolean = false,
        beforeCallback: () -> Unit = {},
    ): HttpResponse<String> {
        val start = browser.get("/oauth2/authorization/google" + (intent?.let { "?intent=$it" } ?: ""))
        check(start.statusCode() == 302) { "Expected a redirect to the provider, got ${start.statusCode()}" }

        val authorize = URI.create(start.location())
        check(authorize.toString().startsWith(google.url("/authorize"))) { "Redirected to $authorize" }
        val query = authorize.rawQuery.split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
        }
        check(query["code_challenge_method"] == "S256") { "No PKCE in $authorize" }

        val code = google.issueCode(identity, nonce = query.getValue("nonce"), forged = forged)
        beforeCallback()
        val state = URLEncoder.encode(query.getValue("state"), Charsets.UTF_8)
        return browser.getAbsolute("${query.getValue("redirect_uri")}?code=$code&state=$state")
    }

    private fun signUpWithEmail(email: String, browser: TestBrowser = TestBrowser(port)): String {
        val attemptId = browser.post("/auth/sign-up/start", """{"email":"$email"}""").field("attemptId")
        val code = codes.lastCodeFor(email)
        val completed = browser.post("/auth/sign-up/complete", """{"attemptId":"$attemptId","code":"$code","password":"correct horse battery"}""")
        check(completed.statusCode() == 201) { "Sign-up failed: ${completed.body()}" }
        return completed.field("accountId")
    }

    private fun verifiedIdentity() =
        Identity("sub-${UUID.randomUUID()}", "social-${UUID.randomUUID()}@gmail.com", emailVerified = true)

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = ApiTestEnvironment.register(registry)
    }
}
