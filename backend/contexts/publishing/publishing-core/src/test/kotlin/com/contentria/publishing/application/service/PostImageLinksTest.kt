package com.contentria.publishing.application.service

import com.contentria.publishing.application.provided.PublishingUseCaseException
import com.contentria.publishing.application.required.MarkdownImages
import com.contentria.publishing.application.required.PostImages
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class PostImageLinksTest {

    /** One image per line of the body, so a test writes "n images" as n lines. */
    private val lines = object : MarkdownImages {
        override fun urlsIn(markdown: String) = markdown.lines().filter { it.isNotBlank() }
    }

    private class Recording(private val failing: Boolean = false) : PostImages {
        val synced = mutableListOf<Triple<UUID, UUID, List<String>>>()
        val released = mutableListOf<UUID>()

        override fun sync(postId: UUID, ownerId: UUID, imageUrls: List<String>) {
            if (failing) error("media is down")
            synced += Triple(postId, ownerId, imageUrls)
        }

        override fun release(postId: UUID) {
            if (failing) error("media is down")
            released += postId
        }
    }

    private fun body(images: Int) = (1..images).joinToString("\n") { "https://img.test/images/$it.png" }

    @Test
    @DisplayName("본문의 이미지는 20개까지다")
    fun `twenty images at most`() {
        val links = PostImageLinks(lines, Recording())

        assertThatCode { links.requireWithinLimit(body(20)) }.doesNotThrowAnyException()
        assertThatThrownBy { links.requireWithinLimit(body(21)) }.isInstanceOf(PublishingUseCaseException.TooManyImages::class.java)
    }

    @Test
    @DisplayName("저장과 삭제 뒤에 본문의 이미지 주소를 그대로 넘긴다")
    fun `tells media`() {
        val media = Recording()
        val links = PostImageLinks(lines, media)
        val post = UUID.randomUUID()
        val owner = UUID.randomUUID()

        links.afterSave(post, owner, body(2))
        links.afterDelete(post)

        assertThat(media.synced).containsExactly(Triple(post, owner, listOf("https://img.test/images/1.png", "https://img.test/images/2.png")))
        assertThat(media.released).containsExactly(post)
    }

    @Test
    @DisplayName("미디어가 실패해도 저장과 삭제는 실패로 바뀌지 않는다")
    fun `a media failure does not undo the save`() {
        val links = PostImageLinks(lines, Recording(failing = true))

        assertThatCode { links.afterSave(UUID.randomUUID(), UUID.randomUUID(), body(1)) }.doesNotThrowAnyException()
        assertThatCode { links.afterDelete(UUID.randomUUID()) }.doesNotThrowAnyException()
    }
}
