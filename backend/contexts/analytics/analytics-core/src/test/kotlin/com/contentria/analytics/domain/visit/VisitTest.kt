package com.contentria.analytics.domain.visit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class VisitTest {

    private val at = Instant.parse("2026-10-21T03:00:00Z")
    private val key = "0123456789abcdef".repeat(4)

    @Test
    @DisplayName("방문은 블로그, 글, 방문자 키, 유입 도메인, 시각만 담는다. 블로그 홈은 글이 없다")
    fun `a visit keeps only these`() {
        val blog = UUID.randomUUID()
        val post = UUID.randomUUID()

        val visit = Visit.record(blog, post, key, "https://www.google.com/search?q=contentria", at)
        val home = Visit.record(blog, null, key, null, at)

        assertThat(listOf(visit.blogId, visit.postId, visit.visitorKey, visit.referrerHost, visit.visitedAt))
            .containsExactly(blog, post, key, "www.google.com", at)
        assertThat(home.postId).isNull()
        assertThat(home.referrerHost).isNull()
        assertThat(visit.id).isNotEqualTo(home.id)
    }

    @Test
    @DisplayName("유입 주소는 웹 주소의 도메인만 소문자로 남긴다. 경로, 검색어, 계정 정보, 다른 형식은 남기지 않는다")
    fun `referrer host only`() {
        assertThat(Visit.hostOf("https://Blog.Example.COM/posts/1?utm=x#top")).isEqualTo("blog.example.com")
        assertThat(Visit.hostOf("http://user:secret@example.com:8080/a")).isEqualTo("example.com")
        assertThat(Visit.hostOf("  https://example.com  ")).isEqualTo("example.com")
        assertThat(Visit.hostOf("android-app://com.google.android.gm/")).isNull()
        assertThat(Visit.hostOf("javascript:alert(1)")).isNull()
        assertThat(Visit.hostOf("not a url")).isNull()
        assertThat(Visit.hostOf("")).isNull()
        assertThat(Visit.hostOf(null)).isNull()
    }

    @Test
    @DisplayName("방문자 키는 소문자 16진수 64자다. IP 같은 원문은 받지 않는다")
    fun `visitor key shape`() {
        val blog = UUID.randomUUID()

        assertThatThrownBy { Visit.record(blog, null, "203.0.113.7", null, at) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Visit.record(blog, null, key.uppercase(), null, at) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Visit.record(blog, null, key.dropLast(1), null, at) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
