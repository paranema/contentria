package com.contentria.publishing.domain.blog

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class BlogTest {

    private val owner = UUID.randomUUID()

    @Test
    @DisplayName("slug는 앞뒤 공백을 빼고 소문자로 맞춘다")
    fun `normalises the slug`() {
        assertThat(Blog.create(owner, "  My-Blog ", "제목").slug).isEqualTo("my-blog")
    }

    @ParameterizedTest
    @ValueSource(strings = ["ab", "abcdefghijklmnopqrstuvwxyz01234", "-blog", "blog-", "my--blog", "my_blog", "블로그"])
    @DisplayName("slug는 3~30자의 영문 소문자, 숫자, 하이픈이다")
    fun `rejects unusable slugs`(slug: String) {
        assertThatThrownBy { Blog.create(owner, slug, "제목") }.isInstanceOf(BlogException.InvalidSlug::class.java)
    }

    @Test
    @DisplayName("제목은 앞뒤 공백을 뺀 뒤 1~50자다")
    fun `title length`() {
        assertThat(Blog.create(owner, "blog", "  제목  ").title).isEqualTo("제목")
        assertThatThrownBy { Blog.create(owner, "blog", "   ") }.isInstanceOf(BlogException.InvalidTitle::class.java)
        assertThatThrownBy { Blog.create(owner, "blog", "가".repeat(51)) }.isInstanceOf(BlogException.InvalidTitle::class.java)
    }

    @Test
    @DisplayName("설명은 없거나 비어 있으면 없음으로 두고, 300자를 넘으면 거부한다")
    fun `description`() {
        assertThat(Blog.create(owner, "blog", "제목", "  ").description).isNull()
        assertThatThrownBy { Blog.create(owner, "blog", "제목", "가".repeat(301)) }
            .isInstanceOf(BlogException.InvalidDescription::class.java)
    }

    @Test
    @DisplayName("소유자는 제목과 설명만 바꾼다. slug와 소유자는 그대로다")
    fun `settings change title and description only`() {
        val blog = Blog.create(owner, "blog", "제목", "설명")

        blog.changeSettings(" 새 제목 ", null)

        assertThat(blog.title).isEqualTo("새 제목")
        assertThat(blog.description).isNull()
        assertThat(blog.slug).isEqualTo("blog")
        assertThat(blog.isOwnedBy(owner)).isTrue()
        assertThat(blog.isOwnedBy(UUID.randomUUID())).isFalse()
    }
}
