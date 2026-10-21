package com.contentria.media.domain.image

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MediaTest {

    private val uploader = UUID.randomUUID()
    private val at = Instant.parse("2026-10-21T00:00:00Z")

    private fun ready(): Media = Media.prepare(uploader, "image/png", 1024, "photo.png").also { it.complete(at) }

    @Test
    @DisplayName("업로드를 준비하면 주소가 정해지고 업로드 중이다. 파일 이름은 주소에 쓰지 않는다")
    fun `preparing fixes the address`() {
        val media = Media.prepare(uploader, "image/jpeg", 2048, "  ../../여름 바다.jpeg  ")

        assertThat(media.status).isEqualTo(MediaStatus.UPLOADING)
        assertThat(media.storedKey).isEqualTo("images/${media.id}.jpg")
        assertThat(media.fileName).isEqualTo("../../여름 바다.jpeg")
        assertThat(media.unattachedSince).isNull()
        assertThat(media.attachedPostIds).isEmpty()
    }

    @Test
    @DisplayName("허용하지 않은 형식, 0바이트, 10MB를 넘는 크기, 빈 이름은 준비하지 않는다")
    fun `preparation refusals`() {
        assertThatThrownBy { Media.prepare(uploader, "image/svg+xml", 10, "a.svg") }.isInstanceOf(MediaException.UnsupportedFormat::class.java)
        assertThatThrownBy { Media.prepare(uploader, "image/png", 0, "a.png") }.isInstanceOf(MediaException.InvalidSize::class.java)
        assertThatThrownBy { Media.prepare(uploader, "image/png", Media.MAX_SIZE_BYTES + 1, "a.png") }.isInstanceOf(MediaException.InvalidSize::class.java)
        assertThatThrownBy { Media.prepare(uploader, "image/png", 10, "   ") }.isInstanceOf(MediaException.InvalidFileName::class.java)
        assertThatThrownBy { Media.prepare(uploader, "image/png", 10, "a".repeat(256)) }.isInstanceOf(MediaException.InvalidFileName::class.java)
        assertThat(Media.prepare(uploader, "image/png", Media.MAX_SIZE_BYTES, "a.png").sizeBytes).isEqualTo(Media.MAX_SIZE_BYTES)
    }

    @Test
    @DisplayName("완료하면 쓸 수 있고 정리 기준 시각이 정해진다. 두 번째 완료는 아무것도 바꾸지 않는다")
    fun `completing`() {
        val media = Media.prepare(uploader, "image/gif", 10, "a.gif")

        assertThat(media.complete(at)).isTrue()
        assertThat(media.complete(at.plusSeconds(60))).isFalse()

        assertThat(media.status).isEqualTo(MediaStatus.READY)
        assertThat(media.unattachedSince).isEqualTo(at)
    }

    @Test
    @DisplayName("완료한 이미지만, 올린 회원의 글에만 연결한다. 연결되면 정리 기준 시각이 빈다")
    fun `attaching`() {
        val uploading = Media.prepare(uploader, "image/png", 10, "a.png")
        val media = ready()
        val post = UUID.randomUUID()

        assertThat(uploading.attachTo(post, uploader)).isFalse()
        assertThat(media.attachTo(post, UUID.randomUUID())).isFalse()
        assertThat(media.unattachedSince).isEqualTo(at)

        assertThat(media.attachTo(post, uploader)).isTrue()
        assertThat(media.attachTo(post, uploader)).isTrue()
        assertThat(media.attachedPostIds).containsExactly(post)
        assertThat(media.unattachedSince).isNull()
    }

    @Test
    @DisplayName("여러 글에 연결될 수 있고, 마지막 글의 연결이 풀릴 때부터 정리 기준 시각을 센다")
    fun `detaching`() {
        val media = ready()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        media.attachTo(first, uploader)
        media.attachTo(second, uploader)

        media.detachFrom(first, at.plusSeconds(10))
        assertThat(media.unattachedSince).isNull()
        media.detachFrom(UUID.randomUUID(), at.plusSeconds(15))
        assertThat(media.unattachedSince).isNull()

        media.detachFrom(second, at.plusSeconds(20))
        assertThat(media.attachedPostIds).isEmpty()
        assertThat(media.unattachedSince).isEqualTo(at.plusSeconds(20))

        media.detachFrom(second, at.plusSeconds(30))
        assertThat(media.unattachedSince).isEqualTo(at.plusSeconds(20))
    }

    @Test
    @DisplayName("글에 연결된 이미지는 지울 수 없다")
    fun `deleting`() {
        val media = ready()
        media.requireDeletable()

        media.attachTo(UUID.randomUUID(), uploader)

        assertThatThrownBy { media.requireDeletable() }.isInstanceOf(MediaException.Attached::class.java)
    }
}
