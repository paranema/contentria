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

    @ParameterizedTest
    @ValueSource(strings = ["admin", "notice", "Notice", "api", "contentria", "contentria-official", "my-contentria"])
    @DisplayName("예약된 slug와 플랫폼 이름이 들어간 slug는 회원이 가질 수 없다")
    fun `rejects reserved slugs`(slug: String) {
        assertThatThrownBy { Blog.create(owner, slug, "제목") }.isInstanceOf(BlogException.ReservedSlug::class.java)
    }

    @Test
    @DisplayName("예약어를 포함하기만 한 slug는 쓸 수 있다")
    fun `a reserved word inside a slug is fine`() {
        assertThat(Blog.create(owner, "admin-kim", "제목").slug).isEqualTo("admin-kim")
        assertThat(Blog.create(owner, "my-notice-board", "제목").slug).isEqualTo("my-notice-board")
    }

    @Test
    @DisplayName("예약어는 모두 slug 형식에 맞는다. 형식에 맞지 않는 예약어는 막을 필요가 없어 실수다")
    fun `reserved slugs are well formed`() {
        assertThat(Blog.RESERVED_SLUGS).allSatisfy { assertThat(it).matches("^[a-z0-9]+(-[a-z0-9]+)*$").hasSizeGreaterThanOrEqualTo(2) }
    }

    @Test
    @DisplayName("제목은 앞뒤 공백을 뺀 뒤 1~50자다")
    fun `title length`() {
        assertThat(Blog.create(owner, "my-blog", "  제목  ").title).isEqualTo("제목")
        assertThatThrownBy { Blog.create(owner, "my-blog", "   ") }.isInstanceOf(BlogException.InvalidTitle::class.java)
        assertThatThrownBy { Blog.create(owner, "my-blog", "가".repeat(51)) }.isInstanceOf(BlogException.InvalidTitle::class.java)
    }

    @Test
    @DisplayName("설명은 없거나 비어 있으면 없음으로 두고, 300자를 넘으면 거부한다")
    fun `description`() {
        assertThat(Blog.create(owner, "my-blog", "제목", "  ").description).isNull()
        assertThatThrownBy { Blog.create(owner, "my-blog", "제목", "가".repeat(301)) }
            .isInstanceOf(BlogException.InvalidDescription::class.java)
    }

    @Test
    @DisplayName("소유자는 제목과 설명만 바꾼다. slug와 소유자는 그대로다")
    fun `settings change title and description only`() {
        val blog = Blog.create(owner, "my-blog", "제목", "설명")

        blog.changeSettings(" 새 제목 ", null)

        assertThat(blog.title).isEqualTo("새 제목")
        assertThat(blog.description).isNull()
        assertThat(blog.slug).isEqualTo("my-blog")
        assertThat(blog.isOwnedBy(owner)).isTrue()
        assertThat(blog.isOwnedBy(UUID.randomUUID())).isFalse()
    }
}
