package com.contentria.analytics.application.visit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class VisitorKeysTest {

    private val keys = VisitorKeys(UUID.randomUUID().toString() + UUID.randomUUID())
    private val day = LocalDate.parse("2026-10-21")

    @Test
    @DisplayName("같은 날, 같은 IP와 브라우저는 같은 키다. 키는 IP를 담지 않는 16진수 64자다")
    fun `same reader, same day`() {
        val key = keys.of(day, "203.0.113.7", "Browser/1.0")

        assertThat(keys.of(day, "203.0.113.7", "Browser/1.0")).isEqualTo(key)
        assertThat(key).matches("[0-9a-f]{64}").doesNotContain("203", "113")
    }

    @Test
    @DisplayName("날짜, IP, 브라우저 중 하나만 달라도 다른 키다. 날이 바뀌면 같은 사람도 다른 키다")
    fun `any difference, another key`() {
        val key = keys.of(day, "203.0.113.7", "Browser/1.0")

        assertThat(keys.of(day.plusDays(1), "203.0.113.7", "Browser/1.0")).isNotEqualTo(key)
        assertThat(keys.of(day, "203.0.113.8", "Browser/1.0")).isNotEqualTo(key)
        assertThat(keys.of(day, "203.0.113.7", "Browser/2.0")).isNotEqualTo(key)
        assertThat(keys.of(day, "203.0.113.7", null)).isNotEqualTo(key)
    }

    @Test
    @DisplayName("경계를 옮겨 이어 붙인 입력은 같은 키가 되지 않는다")
    fun `parts do not run into each other`() {
        assertThat(keys.of(day, "1.2.3.4", "5 Browser")).isNotEqualTo(keys.of(day, "1.2.3.45", " Browser"))
    }

    @Test
    @DisplayName("다른 비밀 키로는 다른 값이 나오고, 짧은 비밀 키는 받지 않는다")
    fun `the secret matters`() {
        val other = VisitorKeys(UUID.randomUUID().toString() + UUID.randomUUID())

        assertThat(other.of(day, "203.0.113.7", "Browser/1.0")).isNotEqualTo(keys.of(day, "203.0.113.7", "Browser/1.0"))
        assertThatThrownBy { VisitorKeys("short") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
