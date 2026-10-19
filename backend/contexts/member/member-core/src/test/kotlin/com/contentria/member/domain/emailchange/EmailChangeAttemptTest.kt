package com.contentria.member.domain.emailchange

import com.contentria.member.domain.verification.CodeVerification
import com.contentria.member.domain.verification.EmailCodeAttemptContract
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** The contract's six-digit code is split into the two addresses' codes, three digits each. */
class EmailChangeAttemptTest : EmailCodeAttemptContract() {

    override fun start(email: String, code: String, now: Instant) =
        EmailChangeAttempt.start(UUID.randomUUID(), email, code.take(3), code.drop(3), now)

    private val now = Instant.parse("2026-10-19T00:00:00Z")

    @Test
    @DisplayName("두 주소의 코드가 모두 맞아야 확인된다. 하나만 맞으면 한 번의 실패다")
    fun `both codes must match`() {
        val attempt = EmailChangeAttempt.start(UUID.randomUUID(), "new@contentria.com", "111111", "222222", now)

        assertThat(attempt.verify("111111", "000000", now)).isEqualTo(CodeVerification.MISMATCH)
        assertThat(attempt.verify("000000", "222222", now)).isEqualTo(CodeVerification.MISMATCH)
        assertThat(attempt.verify("222222", "111111", now)).isEqualTo(CodeVerification.MISMATCH)
        assertThat(attempt.failedAttempts).isEqualTo(3)
        assertThat(attempt.verify("111111", "222222", now)).isEqualTo(CodeVerification.VERIFIED)
    }

    @Test
    @DisplayName("담는 주소는 새 주소다")
    fun `holds the new address`() {
        val accountId = UUID.randomUUID()
        val attempt = EmailChangeAttempt.start(accountId, " New@Contentria.com ", "111111", "222222", now)

        assertThat(attempt.email).isEqualTo("new@contentria.com")
        assertThat(attempt.accountId).isEqualTo(accountId)
    }
}
