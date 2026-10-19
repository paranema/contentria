package com.contentria.member.domain.login

import com.contentria.member.domain.verification.EmailCodeAttemptContract
import java.time.Instant

class LoginAttemptTest : EmailCodeAttemptContract() {
    override fun start(email: String, code: String, now: Instant) = LoginAttempt.start(email, code, now)
}
