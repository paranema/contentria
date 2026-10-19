package com.contentria.member.domain.signup

import com.contentria.member.domain.verification.EmailCodeAttemptContract
import java.time.Instant

class SignUpAttemptTest : EmailCodeAttemptContract() {
    override fun start(email: String, code: String, now: Instant) = SignUpAttempt.start(email, code, now)
}
