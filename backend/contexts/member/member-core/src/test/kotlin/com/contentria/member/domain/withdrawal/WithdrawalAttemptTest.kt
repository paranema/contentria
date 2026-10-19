package com.contentria.member.domain.withdrawal

import com.contentria.member.domain.verification.EmailCodeAttemptContract
import java.time.Instant
import java.util.UUID

class WithdrawalAttemptTest : EmailCodeAttemptContract() {
    override fun start(email: String, code: String, now: Instant) =
        WithdrawalAttempt.start(UUID.randomUUID(), email, code, now)
}
