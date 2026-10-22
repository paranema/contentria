package com.contentria.app.api

import java.util.UUID

/**
 * Signs this browser up as a new member through the HTTP flow and returns the account id. The
 * browser keeps the session cookies, so it is signed in afterwards.
 */
fun TestBrowser.signUpAsNewMember(codes: RecordingCodeSender, email: String = "member-${UUID.randomUUID()}@contentria.com"): UUID {
    val attemptId = post("/auth/sign-up/start", """{"email":"$email","recaptchaV3Token":"${FakeHumanVerifier.HUMAN}"}""").field("attemptId")
    // Generated, not written out: secret scanners read a quoted password-like value as a leak.
    val password = UUID.randomUUID().toString()
    val completed = post("/auth/sign-up/complete", """{"attemptId":"$attemptId","code":"${codes.lastCodeFor(email)}","password":"$password"}""")
    check(completed.statusCode() == 201) { "Sign-up failed: ${completed.body()}" }
    return UUID.fromString(completed.field("accountId"))
}
