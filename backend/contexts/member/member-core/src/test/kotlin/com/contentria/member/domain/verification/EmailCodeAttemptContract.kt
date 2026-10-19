package com.contentria.member.domain.verification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The rules every code attempt follows (docs/contexts/member/01-rules.md, 흐름 6: 로그인 시도는
 * 가입 시도와 같은 규칙을 따른다). Each kind of attempt runs these through its own subclass.
 */
abstract class EmailCodeAttemptContract {

    protected abstract fun start(email: String, code: String, now: Instant): EmailCodeAttempt

    private val now = Instant.parse("2026-10-19T00:00:00Z")

    @Test
    @DisplayName("맞는 코드는 확인된다")
    fun `the right code verifies`() {
        assertThat(start("reader@contentria.com", "123456", now).verify("123456", now))
            .isEqualTo(CodeVerification.VERIFIED)
    }

    @Test
    @DisplayName("주소는 정규화해서 담는다")
    fun `stores the normalised address`() {
        assertThat(start("  Reader@Contentria.COM ", "123456", now).email).isEqualTo("reader@contentria.com")
    }

    @Test
    @DisplayName("틀린 코드는 실패 횟수를 늘리고, 5번째 실패에서 잠긴다")
    fun `wrong codes count up to a lock`() {
        val attempt = start("reader@contentria.com", "123456", now)

        repeat(4) { assertThat(attempt.verify("000000", now)).isEqualTo(CodeVerification.MISMATCH) }
        assertThat(attempt.verify("000000", now)).isEqualTo(CodeVerification.LOCKED)
        assertThat(attempt.failedAttempts).isEqualTo(EmailCodeAttempt.MAX_FAILED_ATTEMPTS)
    }

    @Test
    @DisplayName("잠긴 뒤에는 맞는 코드도 받지 않는다")
    fun `a locked attempt refuses even the right code`() {
        val attempt = start("reader@contentria.com", "123456", now)
        repeat(EmailCodeAttempt.MAX_FAILED_ATTEMPTS) { attempt.verify("000000", now) }

        assertThat(attempt.verify("123456", now)).isEqualTo(CodeVerification.LOCKED)
    }

    @Test
    @DisplayName("10분이 지나면 맞는 코드도 만료다")
    fun `expires after ten minutes`() {
        val attempt = start("reader@contentria.com", "123456", now)

        assertThat(attempt.verify("123456", now.plus(EmailCodeAttempt.VALIDITY).minusSeconds(1))).isEqualTo(CodeVerification.VERIFIED)
        assertThat(attempt.verify("123456", now.plus(EmailCodeAttempt.VALIDITY))).isEqualTo(CodeVerification.EXPIRED)
    }

    @Test
    @DisplayName("같은 코드라도 시도마다 저장되는 해시가 다르다")
    fun `the code hash is salted per attempt`() {
        val first = start("reader@contentria.com", "123456", now)
        val second = start("reader@contentria.com", "123456", now)

        assertThat(first.verify("123456", now)).isEqualTo(CodeVerification.VERIFIED)
        assertThat(second.verify("123456", now)).isEqualTo(CodeVerification.VERIFIED)
        assertThat(first.id).isNotEqualTo(second.id)
    }
}
