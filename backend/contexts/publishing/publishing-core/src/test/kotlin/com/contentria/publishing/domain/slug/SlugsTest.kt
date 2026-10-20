package com.contentria.publishing.domain.slug

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SlugsTest {

    @Test
    @DisplayName("영문은 소문자로, 공백은 하이픈으로 바꾸고 한글은 남긴다")
    fun `keeps Korean, lowercases Latin, hyphenates spaces`() {
        assertThat(Slugs.from("  Kotlin으로 시작하는   첫 백엔드 ", "post", 100)).isEqualTo("kotlin으로-시작하는-첫-백엔드")
    }

    @Test
    @DisplayName("허용하지 않는 문자는 지우고, 하이픈은 하나로 줄이고, 양 끝 하이픈을 뗀다")
    fun `drops punctuation and squeezes hyphens`() {
        assertThat(Slugs.from("--Hello, World!! -- (2026)--", "post", 100)).isEqualTo("hello-world-2026")
    }

    @Test
    @DisplayName("남는 것이 없으면 대체 값을 쓴다. 레거시의 무작위 UUID 대신이다")
    fun `falls back when nothing is left`() {
        assertThat(Slugs.from("!!! ???", "post", 100)).isEqualTo("post")
    }

    @Test
    @DisplayName("최대 길이에서 자르고, 잘린 끝의 하이픈은 뗀다")
    fun `cuts at the maximum length`() {
        assertThat(Slugs.from("abc def ghi", "post", 4)).isEqualTo("abc")
    }

    @Test
    @DisplayName("겹치면 -2, -3을 붙이고, 길이를 넘지 않도록 앞을 줄인다")
    fun `suffixes taken slugs`() {
        assertThat(Slugs.nextAvailable("hello", emptySet(), 100)).isEqualTo("hello")
        assertThat(Slugs.nextAvailable("hello", setOf("hello"), 100)).isEqualTo("hello-2")
        assertThat(Slugs.nextAvailable("hello", setOf("hello", "hello-2"), 100)).isEqualTo("hello-3")
        assertThat(Slugs.nextAvailable("abcdef", setOf("abcdef"), 6)).isEqualTo("abcd-2")
    }

    @Test
    @DisplayName("직접 입력한 slug는 만들기 규칙과 같은 문자만 허용한다")
    fun `validates typed slugs`() {
        assertThat(Slugs.isValid("my-post-2", 100)).isTrue()
        assertThat(Slugs.isValid("첫-글", 100)).isTrue()
        assertThat(Slugs.isValid("-leading", 100)).isFalse()
        assertThat(Slugs.isValid("double--hyphen", 100)).isFalse()
        assertThat(Slugs.isValid("Upper", 100)).isFalse()
        assertThat(Slugs.isValid("a".repeat(101), 100)).isFalse()
    }
}
