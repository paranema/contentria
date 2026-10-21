package com.contentria.media.domain.video

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class VideoTest {

    private val uploader = UUID.randomUUID()
    private val at = Instant.parse("2026-10-21T00:00:00Z")
    private val result = TranscodedVideo("hls/x/master.m3u8", "hls/x/poster.jpg", 12_000, 1280, 720)

    private fun video(): Video = Video.prepare(uploader, "video/mp4", 1024, "clip.mp4", at)

    @Test
    @DisplayName("준비하면 대기이고 원본은 자기 폴더에 둔다. 아직 어떤 글에도 쓰이지 않으므로 정리 기준 시각이 있다")
    fun `preparing`() {
        val video = video()

        assertThat(video.status).isEqualTo(VideoStatus.PENDING)
        assertThat(video.rawKey).isEqualTo("raw/${video.id}/original.mp4")
        assertThat(video.outputPrefix).isEqualTo("hls/${video.id}/")
        assertThat(video.unattachedSince).isEqualTo(at)
        assertThat(Video.prepare(uploader, " Video/QuickTime ", 1, "a.mov", at).rawKey).endsWith(".mov")
    }

    @Test
    @DisplayName("허용하지 않은 형식, 0바이트, 500MB를 넘는 크기, 빈 이름은 준비하지 않는다")
    fun `preparation refusals`() {
        assertThatThrownBy { Video.prepare(uploader, "video/avi", 1, "a.avi", at) }.isInstanceOf(VideoException.UnsupportedFormat::class.java)
        assertThatThrownBy { Video.prepare(uploader, "video/mp4", 0, "a.mp4", at) }.isInstanceOf(VideoException.InvalidSize::class.java)
        assertThatThrownBy { Video.prepare(uploader, "video/mp4", Video.MAX_SIZE_BYTES + 1, "a.mp4", at) }.isInstanceOf(VideoException.InvalidSize::class.java)
        assertThatThrownBy { Video.prepare(uploader, "video/mp4", 1, " ", at) }.isInstanceOf(VideoException.InvalidFileName::class.java)
    }

    @Test
    @DisplayName("변환을 시작해 끝내면 완료다. 완료된 영상에 같은 알림이 다시 와도 바뀌지 않는다")
    fun `transcoding to completion`() {
        val video = video()

        assertThat(video.startTranscoding(at)).isTrue()
        video.complete(result)

        assertThat(video.status).isEqualTo(VideoStatus.COMPLETED)
        assertThat(listOf(video.masterKey, video.posterKey, video.durationMs, video.width, video.height))
            .containsExactly("hls/x/master.m3u8", "hls/x/poster.jpg", 12_000L, 1280, 720)
        assertThat(video.startTranscoding(at.plusSeconds(60))).isFalse()
        video.fail("late")
        assertThat(video.status).isEqualTo(VideoStatus.COMPLETED)
    }

    @Test
    @DisplayName("변환 중인 영상에 알림이 다시 오면 처음부터 다시 맡는다. 변환을 시작하지 않은 영상은 완료할 수 없다")
    fun `restarting and order`() {
        val video = video()
        assertThatThrownBy { video.complete(result) }.isInstanceOf(VideoException.NotTranscoding::class.java)

        video.startTranscoding(at)
        assertThat(video.startTranscoding(at.plusSeconds(600))).isTrue()
        assertThat(video.transcodingStartedAt).isEqualTo(at.plusSeconds(600))
    }

    @Test
    @DisplayName("실패한 영상은 다시 변환하지 않는다. 이유는 500자까지 남긴다")
    fun `failing`() {
        val video = video()

        video.fail("x".repeat(600))

        assertThat(video.status).isEqualTo(VideoStatus.FAILED)
        assertThat(video.failureReason).hasSize(Video.FAILURE_REASON_MAX_LENGTH)
        assertThat(video.startTranscoding(at)).isFalse()
    }

    @Test
    @DisplayName("변환 중인 채 2시간이 지나면 멈춘 것이다")
    fun `stuck`() {
        val video = video()
        assertThat(video.isStuck(at.plus(Duration.ofDays(1)))).isFalse()

        video.startTranscoding(at)

        assertThat(video.isStuck(at.plus(Video.STUCK_AFTER))).isFalse()
        assertThat(video.isStuck(at.plus(Video.STUCK_AFTER).plusSeconds(1))).isTrue()
    }

    @Test
    @DisplayName("올린 회원의 글에만 상태와 상관없이 연결되고, 마지막 연결이 풀린 뒤 7일이 지나면 정리 대상이다")
    fun `attaching and expiry`() {
        val video = video()
        val post = UUID.randomUUID()

        assertThat(video.attachTo(post, UUID.randomUUID())).isFalse()
        assertThat(video.attachTo(post, uploader)).isTrue()
        assertThat(video.isExpired(at.plus(Duration.ofDays(30)))).isFalse()

        val released = at.plus(Duration.ofDays(1))
        video.detachFrom(post, released)
        assertThat(video.isExpired(released.plus(Video.UNUSED_EXPIRY))).isFalse()
        assertThat(video.isExpired(released.plus(Video.UNUSED_EXPIRY).plusSeconds(1))).isTrue()
        assertThat(video().isExpired(at.plus(Video.UNUSED_EXPIRY).plusSeconds(1))).isTrue()
    }
}
