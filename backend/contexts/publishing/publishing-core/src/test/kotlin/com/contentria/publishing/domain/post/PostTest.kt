package com.contentria.publishing.domain.post

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PostTest {

    private val now = Instant.parse("2026-10-20T00:00:00Z")

    private fun content(title: String = "첫 글", body: String = "본문", summary: PostSummary = PostSummary.derived("본문")) =
        PostContent(title, body, summary)

    private fun draft(slug: String = "첫-글") = Post.draft(UUID.randomUUID(), content(), slug)

    private fun published(slug: String = "첫-글") = draft(slug).apply { publish(now) }

    @Nested
    @DisplayName("작성")
    inner class Writing {

        @Test
        @DisplayName("새 글은 임시저장으로 시작하고 발행 시각이 없다")
        fun `starts as a draft`() {
            val post = draft()

            assertThat(post.status).isEqualTo(PostStatus.DRAFT)
            assertThat(post.publishedAt).isNull()
            assertThat(post.slug).isEqualTo("첫-글")
            assertThat(post.oldSlugs).isEmpty()
        }

        @Test
        @DisplayName("제목은 앞뒤 공백을 뺀 뒤 1~150자, 본문은 10만 자, 요약은 300자까지다")
        fun `lengths`() {
            assertThat(Post.draft(UUID.randomUUID(), content(title = "  제목  "), "s").title).isEqualTo("제목")
            assertThatThrownBy { Post.draft(UUID.randomUUID(), content(title = " "), "s") }.isInstanceOf(PostException.InvalidTitle::class.java)
            assertThatThrownBy { Post.draft(UUID.randomUUID(), content(title = "가".repeat(151)), "s") }.isInstanceOf(PostException.InvalidTitle::class.java)
            assertThatThrownBy { Post.draft(UUID.randomUUID(), content(body = "가".repeat(100_001)), "s") }.isInstanceOf(PostException.InvalidBody::class.java)
            assertThatThrownBy { PostSummary.written("가".repeat(301)) }.isInstanceOf(PostException.InvalidSummary::class.java)
        }

        @Test
        @DisplayName("요약은 작성자가 썼는지 본문에서 뽑았는지를 함께 담는다")
        fun `summary keeps its origin`() {
            val post = draft()
            assertThat(post.summaryWrittenByAuthor).isFalse()

            post.revise(content(summary = PostSummary.written(" 직접 쓴 요약 ")), categoryId = null)

            assertThat(post.summary).isEqualTo("직접 쓴 요약")
            assertThat(post.summaryWrittenByAuthor).isTrue()
        }

        @Test
        @DisplayName("카테고리 없는 글을 허용한다")
        fun `no category`() {
            val post = draft()
            val category = UUID.randomUUID()

            post.revise(content(), category)
            assertThat(post.categoryId).isEqualTo(category)
            post.revise(content(), null)
            assertThat(post.categoryId).isNull()
        }

        @Test
        @DisplayName("쓸 수 없는 slug는 거부한다")
        fun `invalid slug`() {
            assertThatThrownBy { draft("Not Valid") }.isInstanceOf(PostException.InvalidSlug::class.java)
        }
    }

    @Nested
    @DisplayName("발행과 공개 범위")
    inner class Publication {

        @Test
        @DisplayName("발행하면 발행 시각이 정해지고, 비공개로 돌렸다 다시 공개해도 그대로다")
        fun `published time is set once`() {
            val post = published()
            assertThat(post.status).isEqualTo(PostStatus.PUBLISHED)
            assertThat(post.publishedAt).isEqualTo(now)

            post.hide()
            assertThat(post.status).isEqualTo(PostStatus.PRIVATE)
            post.republish()

            assertThat(post.status).isEqualTo(PostStatus.PUBLISHED)
            assertThat(post.publishedAt).isEqualTo(now)
        }

        @Test
        @DisplayName("발행은 임시저장에서만, 비공개는 발행에서만, 다시 공개는 비공개에서만 된다")
        fun `transitions`() {
            assertThatThrownBy { published().publish(now) }.isInstanceOf(PostException.StatusTransitionNotAllowed::class.java)
            assertThatThrownBy { draft().hide() }.isInstanceOf(PostException.StatusTransitionNotAllowed::class.java)
            assertThatThrownBy { published().republish() }.isInstanceOf(PostException.StatusTransitionNotAllowed::class.java)
            assertThatThrownBy { published().apply { hide() }.publish(now) }.isInstanceOf(PostException.StatusTransitionNotAllowed::class.java)
        }

        @Test
        @DisplayName("발행된 글만 누구나 본다")
        fun `only published is public`() {
            assertThat(PostStatus.entries.filter { it.isPublic }).containsExactly(PostStatus.PUBLISHED)
        }
    }

    @Nested
    @DisplayName("slug")
    inner class Slug {

        @Test
        @DisplayName("임시저장 글은 제목을 따라 slug가 바뀌고 옛 slug를 남기지 않는다")
        fun `a draft follows its title`() {
            val post = draft("첫-글")

            post.revise(content(title = "고친 제목"), categoryId = null, draftSlug = "고친-제목")

            assertThat(post.slug).isEqualTo("고친-제목")
            assertThat(post.oldSlugs).isEmpty()
            assertThat(post.hasSlug("첫-글")).isFalse()
        }

        @Test
        @DisplayName("발행한 글은 제목이 바뀌어도 slug가 따라가지 않는다")
        fun `a published post does not follow its title`() {
            val post = published()

            assertThatThrownBy { post.revise(content(title = "고친 제목"), categoryId = null, draftSlug = "고친-제목") }
                .isInstanceOf(PostException.SlugFollowsTitleOnlyInDraft::class.java)

            post.revise(content(title = "고친 제목"), categoryId = null)
            assertThat(post.slug).isEqualTo("첫-글")
        }

        @Test
        @DisplayName("발행한 글의 slug를 바꾸면 옛 slug가 남고, 옛 slug로 되돌리면 새로 쌓이지 않는다")
        fun `a published post keeps old slugs`() {
            val post = published("첫-글")

            post.changeSlug("두번째-주소")
            assertThat(post.slug).isEqualTo("두번째-주소")
            assertThat(post.oldSlugs).containsExactly("첫-글")

            post.changeSlug("세번째-주소")
            assertThat(post.oldSlugs).containsExactlyInAnyOrder("첫-글", "두번째-주소")

            post.changeSlug("첫-글")
            assertThat(post.slug).isEqualTo("첫-글")
            assertThat(post.oldSlugs).containsExactlyInAnyOrder("두번째-주소", "세번째-주소")
            assertThat(post.hasSlug("세번째-주소")).isTrue()
        }

        @Test
        @DisplayName("비공개 글도 공개된 적이 있으므로 옛 slug를 남긴다")
        fun `a private post keeps old slugs too`() {
            val post = published("첫-글").apply { hide() }

            post.changeSlug("새-주소")

            assertThat(post.oldSlugs).containsExactly("첫-글")
        }

        @Test
        @DisplayName("같은 slug로 바꾸면 아무 일도 없다")
        fun `same slug is a no-op`() {
            val post = published("첫-글")
            post.changeSlug("첫-글")
            assertThat(post.oldSlugs).isEmpty()
        }
    }
}
