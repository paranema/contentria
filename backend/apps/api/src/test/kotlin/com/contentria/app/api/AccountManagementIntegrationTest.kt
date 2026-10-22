package com.contentria.app.api

import com.contentria.app.api.FakeOidcProvider.Identity
import com.contentria.member.api.AccountStatusView
import com.contentria.member.api.GetAccountStatusQuery
import com.contentria.member.application.required.AccountNotice
import com.contentria.member.application.required.CodePurpose
import com.contentria.member.application.required.PasswordLinkPurpose
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.UUID

/**
 * docs/contexts/member/01-rules.md, 흐름 4. 계정 관리, over HTTP. Each test also checks 공통의 세션
 * 무효화: a second browser signed in to the same account loses its session.
 */
class AccountManagementIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    @Autowired
    private lateinit var links: RecordingPasswordLinkSender

    @Autowired
    private lateinit var notices: RecordingNoticeSender

    @Autowired
    private lateinit var accountStatus: GetAccountStatusQuery

    // Generated without any string literal: a quoted value assigned to a password-like name reads
    // as a leaked secret to secret scanners, even when the value is random.
    private val password = UUID.randomUUID().toString()
    private val newPassword = UUID.randomUUID().toString()

    @Test
    @DisplayName("현재 비밀번호를 확인하고 바꾸면, 이 브라우저만 로그인 상태로 남는다")
    fun `change password`() {
        val email = newEmail()
        val browser = TestBrowser(port)
        signUp(email, browser)
        val otherDevice = signedIn(email, password)

        val wrong = browser.post("/me/password", changePassword(UUID.randomUUID().toString(), newPassword))
        assertThat(wrong.statusCode()).isEqualTo(400)
        assertThat(wrong.body()).contains("CURRENT_PASSWORD_MISMATCH")

        assertThat(browser.post("/me/password", changePassword(password, newPassword)).statusCode()).isEqualTo(200)
        assertThat(browser.get("/me/profile").statusCode()).isEqualTo(200)
        assertThat(browser.post("/auth/refresh", "").statusCode()).isEqualTo(200)
        assertThat(otherDevice.post("/auth/refresh", "").statusCode()).isEqualTo(401)

        assertThat(login(email, password).statusCode()).isEqualTo(401)
        assertThat(login(email, newPassword).statusCode()).isEqualTo(200)
        assertThat(notices.sentTo(email)).contains(AccountNotice.PasswordChanged)
    }

    @Test
    @DisplayName("소셜 전용 계정은 메일로 받은 설정 링크로만 비밀번호를 정한다. 링크는 한 번만 쓴다")
    fun `password setup link for a social-only account`() {
        val email = "social-${UUID.randomUUID()}@gmail.com"
        val browser = TestBrowser(port)
        browser.signInWithGoogle(Identity("sub-${UUID.randomUUID()}", email, emailVerified = true))

        assertThat(browser.post("/me/password", changePassword(password, newPassword)).body()).contains("PASSWORD_NOT_SET")
        assertThat(browser.post("/me/password/setup-link", "").statusCode()).isEqualTo(202)
        val token = links.lastTokenFor(email, PasswordLinkPurpose.SETUP)

        val reset = TestBrowser(port).post("/auth/password/reset", resetPassword(token, newPassword))
        assertThat(reset.statusCode()).isEqualTo(200)
        assertThat(login(email, newPassword).statusCode()).isEqualTo(200)

        val reused = TestBrowser(port).post("/auth/password/reset", resetPassword(token, UUID.randomUUID().toString()))
        assertThat(reused.statusCode()).isEqualTo(400)
        assertThat(reused.body()).contains("INVALID_PASSWORD_TOKEN")

        // 구글로 연 세션은 끝났다. 비밀번호가 생겼으므로 설정 링크는 더 받지 않는다.
        assertThat(browser.post("/auth/refresh", "").statusCode()).isEqualTo(401)
        val signedIn = signedIn(email, newPassword)
        assertThat(signedIn.post("/me/password/setup-link", "").body()).contains("PASSWORD_ALREADY_SET")
        assertThat(signedIn.get("/me/credentials").body()).isEqualTo("""{"providers":["EMAIL","GOOGLE"]}""")
    }

    @Test
    @DisplayName("비밀번호 분실: 없는 주소에도 같은 답을 주고, 다시 요청하면 이전 링크는 무효다")
    fun `forgotten password`() {
        val email = newEmail()
        signUp(email)
        val otherDevice = signedIn(email, password)
        val unknown = newEmail()

        assertThat(TestBrowser(port).post("/auth/password/reset-link", """{"email":"$unknown","recaptchaV3Token":"${FakeHumanVerifier.HUMAN}"}""").statusCode()).isEqualTo(202)
        assertThat(links.sentAnyTo(unknown)).isFalse()

        assertThat(TestBrowser(port).post("/auth/password/reset-link", """{"email":"$email","recaptchaV3Token":"${FakeHumanVerifier.HUMAN}"}""").statusCode()).isEqualTo(202)
        val first = links.lastTokenFor(email, PasswordLinkPurpose.RESET)
        TestBrowser(port).post("/auth/password/reset-link", """{"email":"$email","recaptchaV3Token":"${FakeHumanVerifier.HUMAN}"}""")
        val second = links.lastTokenFor(email, PasswordLinkPurpose.RESET)

        assertThat(TestBrowser(port).post("/auth/password/reset", resetPassword(first, newPassword)).body()).contains("INVALID_PASSWORD_TOKEN")
        assertThat(TestBrowser(port).post("/auth/password/reset", resetPassword(second, newPassword)).statusCode()).isEqualTo(200)
        assertThat(login(email, newPassword).statusCode()).isEqualTo(200)
        assertThat(otherDevice.post("/auth/refresh", "").statusCode()).isEqualTo(401)
    }

    @Test
    @DisplayName("이메일 변경은 현재 주소와 새 주소의 코드가 모두 맞아야 반영되고, 옛 주소로 보낸 링크는 무효가 된다")
    fun `email change`() {
        val email = newEmail()
        val browser = TestBrowser(port)
        signUp(email, browser)
        val otherDevice = signedIn(email, password)
        val taken = newEmail().also { signUp(it) }

        // 변경 전에 옛 주소로 나간 재설정 링크
        TestBrowser(port).post("/auth/password/reset-link", """{"email":"$email","recaptchaV3Token":"${FakeHumanVerifier.HUMAN}"}""")
        val oldLink = links.lastTokenFor(email, PasswordLinkPurpose.RESET)

        assertThat(browser.post("/me/email-change/start", """{"newEmail":"${email.uppercase()}"}""").body()).contains("EMAIL_UNCHANGED")
        assertThat(browser.post("/me/email-change/start", """{"newEmail":"$taken"}""").body()).contains("EMAIL_IN_USE")

        val newAddress = newEmail()
        val attemptId = browser.post("/me/email-change/start", """{"newEmail":"$newAddress"}""").field("attemptId")
        val currentCode = codes.lastCodeFor(email, CodePurpose.EMAIL_CHANGE_CURRENT)
        val newCode = codes.lastCodeFor(newAddress, CodePurpose.EMAIL_CHANGE_NEW)

        assertThat(browser.post("/me/email-change/complete", emailChange(attemptId, newCode, currentCode)).body()).contains("VERIFICATION_MISMATCH")
        // 다른 계정은 남의 시도를 완료할 수 없다
        assertThat(signedIn(taken, password).post("/me/email-change/complete", emailChange(attemptId, currentCode, newCode)).body())
            .contains("VERIFICATION_EXPIRED")
        assertThat(browser.get("/me/profile").field("email")).isEqualTo(email)

        assertThat(browser.post("/me/email-change/complete", emailChange(attemptId, currentCode, newCode)).statusCode()).isEqualTo(200)
        assertThat(browser.get("/me/profile").field("email")).isEqualTo(newAddress)
        assertThat(login(newAddress, password).statusCode()).isEqualTo(200)
        assertThat(login(email, password).statusCode()).isEqualTo(401)
        assertThat(otherDevice.post("/auth/refresh", "").statusCode()).isEqualTo(401)
        assertThat(notices.sentTo(email)).contains(AccountNotice.EmailChanged)
        assertThat(TestBrowser(port).post("/auth/password/reset", resetPassword(oldLink, newPassword)).body()).contains("INVALID_PASSWORD_TOKEN")
    }

    @Test
    @DisplayName("탈퇴는 계정 주소로 받은 코드로 확정한다. 자격증명과 주소를 놓아주고, 프로필은 탈퇴 표시만 남긴다")
    fun `withdrawal`() {
        val email = newEmail()
        val browser = TestBrowser(port)
        val accountId = UUID.fromString(signUp(email, browser))
        val otherDevice = signedIn(email, password)
        val displayName = browser.get("/me/profile").field("displayName")
        assertThat(accountStatus.get(accountId)?.status).isEqualTo(AccountStatusView.Status.ACTIVE)

        val attemptId = browser.post("/me/withdrawal/start", "").field("attemptId")
        val code = codes.lastCodeFor(email, CodePurpose.WITHDRAWAL)
        assertThat(browser.post("/me/withdrawal/complete", withdrawal(attemptId, wrongCodeFor(code))).body()).contains("VERIFICATION_MISMATCH")

        val completed = browser.post("/me/withdrawal/complete", withdrawal(attemptId, code))
        assertThat(completed.statusCode()).isEqualTo(204)
        assertThat(browser.cookie("accessToken")).isNull()
        assertThat(browser.cookie("refreshToken")).isNull()
        assertThat(otherDevice.post("/auth/refresh", "").statusCode()).isEqualTo(401)
        assertThat(login(email, password).statusCode()).isEqualTo(401)

        val account = ApiTestEnvironment.row("select status, email from member.accounts where id = ?", accountId)!!
        assertThat(account["status"]).isEqualTo("DELETED")
        assertThat(account["email"] as String).startsWith("deleted+").endsWith("@deleted.invalid")
        assertThat(ApiTestEnvironment.row("select count(*) as n from member.credentials where account_id = ?", accountId)!!["n"])
            .isEqualTo(0L)
        val profile = ApiTestEnvironment.row("select display_name, withdrawn_at from member.profiles where id = ?", accountId)!!
        assertThat(profile["display_name"]).isEqualTo(displayName)
        assertThat(profile["withdrawn_at"]).isNotNull()

        // 다른 컨텍스트에는 탈퇴로 보인다
        assertThat(accountStatus.get(accountId)?.status).isEqualTo(AccountStatusView.Status.CLOSED)

        // 주소를 놓아줬으므로 같은 주소로 다시 가입할 수 있다
        assertThat(signUp(email)).isNotEqualTo(accountId.toString())
    }

    @Test
    @DisplayName("다른 컨텍스트에 공개하는 계정 상태는 ID 묶음으로 조회하고, 모르는 ID는 결과에 없다")
    fun `account status for other contexts`() {
        val first = UUID.fromString(signUp(newEmail()))
        val second = UUID.fromString(signUp(newEmail()))
        val unknown = UUID.randomUUID()

        val statuses = accountStatus.getAll(setOf(first, second, unknown))

        assertThat(statuses.keys).containsExactlyInAnyOrder(first, second)
        assertThat(statuses.values).allSatisfy { assertThat(it.isActive).isTrue() }
        assertThat(accountStatus.get(unknown)).isNull()
        assertThat(accountStatus.getAll(emptySet())).isEmpty()
    }

    private fun signUp(email: String, browser: TestBrowser = TestBrowser(port)): String {
        val attemptId = browser.post("/auth/sign-up/start", """{"email":"$email","recaptchaV3Token":"${FakeHumanVerifier.HUMAN}"}""").field("attemptId")
        val completed = browser.post(
            "/auth/sign-up/complete",
            """{"attemptId":"$attemptId","code":"${codes.lastCodeFor(email)}","password":"$password"}""",
        )
        check(completed.statusCode() == 201) { "Sign-up failed: ${completed.body()}" }
        return completed.field("accountId")
    }

    /** Another browser signed in to the same account: the session that invalidation must end. */
    private fun signedIn(email: String, password: String): TestBrowser =
        TestBrowser(port).also { check(it.post("/auth/login", """{"email":"$email","password":"$password","recaptchaV3Token":"${FakeHumanVerifier.HUMAN}"}""").statusCode() == 200) }

    private fun login(email: String, password: String) =
        TestBrowser(port).post("/auth/login", """{"email":"$email","password":"$password","recaptchaV3Token":"${FakeHumanVerifier.HUMAN}"}""")

    private fun newEmail() = "member-${UUID.randomUUID()}@contentria.com"

    private fun changePassword(current: String, new: String) = """{"currentPassword":"$current","newPassword":"$new"}"""
    private fun resetPassword(token: String, password: String) = """{"token":"$token","newPassword":"$password"}"""
    private fun emailChange(attemptId: String, current: String, new: String) =
        """{"attemptId":"$attemptId","currentAddressCode":"$current","newAddressCode":"$new"}"""
    private fun withdrawal(attemptId: String, code: String) = """{"attemptId":"$attemptId","code":"$code"}"""
    private fun wrongCodeFor(code: String) = if (code == "000000") "111111" else "000000"
}
