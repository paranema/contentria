package com.contentria.member.domain.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RefreshTokenTest {

    private val now = Instant.parse("2026-10-19T00:00:00Z")

    @Test
    @DisplayName("원값이 아니라 해시만 저장한다")
    fun `stores only a hash of the raw value`() {
        val issued = RefreshToken.issue(UUID.randomUUID(), now, Duration.ofDays(7))

        assertThat(issued.token.tokenHash).isEqualTo(RefreshToken.hashOf(issued.rawValue))
        assertThat(issued.token.tokenHash).isNotEqualTo(issued.rawValue)
    }

    @Test
    @DisplayName("발급할 때마다 다른 값이다")
    fun `every token is different`() {
        val accountId = UUID.randomUUID()
        assertThat(RefreshToken.issue(accountId, now, Duration.ofDays(7)).rawValue)
            .isNotEqualTo(RefreshToken.issue(accountId, now, Duration.ofDays(7)).rawValue)
    }

    @Test
    @DisplayName("만료 시각부터는 쓸 수 없다")
    fun `unusable from its expiry`() {
        val token = RefreshToken.issue(UUID.randomUUID(), now, Duration.ofDays(7)).token

        assertThat(token.isUsableAt(now.plus(Duration.ofDays(7)).minusSeconds(1))).isTrue()
        assertThat(token.isUsableAt(now.plus(Duration.ofDays(7)))).isFalse()
    }
}
