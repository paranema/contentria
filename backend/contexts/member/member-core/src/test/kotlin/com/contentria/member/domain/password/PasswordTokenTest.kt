package com.contentria.member.domain.password

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PasswordTokenTest {

    private val now = Instant.parse("2026-10-19T00:00:00Z")

    @Test
    @DisplayName("원값이 아니라 해시만 저장한다")
    fun `stores only the hash`() {
        val issued = PasswordToken.issue(UUID.randomUUID(), now)

        assertThat(issued.token.tokenHash).isEqualTo(PasswordToken.hashOf(issued.rawValue))
        assertThat(issued.token.tokenHash).isNotEqualTo(issued.rawValue)
    }

    @Test
    @DisplayName("30분이 지나면 쓸 수 없다")
    fun `expires after thirty minutes`() {
        val token = PasswordToken.issue(UUID.randomUUID(), now).token

        assertThat(token.isUsableAt(now.plus(PasswordToken.VALIDITY).minusSeconds(1))).isTrue()
        assertThat(token.isUsableAt(now.plus(PasswordToken.VALIDITY))).isFalse()
    }

    @Test
    @DisplayName("발급할 때마다 다른 값이다")
    fun `every token is different`() {
        val accountId = UUID.randomUUID()

        assertThat(PasswordToken.issue(accountId, now).rawValue).isNotEqualTo(PasswordToken.issue(accountId, now).rawValue)
    }
}
