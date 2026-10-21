package com.contentria.media.domain.image

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ImageFormatTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 0x4A, 0x46, 0x49, 0x46, 0, 1)
    private val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)
    private val webp = bytes(0x52, 0x49, 0x46, 0x46, 0x24, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)
    private val gif89 = "GIF89a\u0001\u0000\u0001\u0000\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
    private val gif87 = "GIF87a\u0001\u0000\u0001\u0000\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
    private val html = "<!DOCTYPE ht".toByteArray()

    @Test
    @DisplayName("각 형식은 자기 시그니처로 시작하는 파일만 맞다고 본다")
    fun `each format knows its own header`() {
        val headers = mapOf(ImageFormat.JPEG to jpeg, ImageFormat.PNG to png, ImageFormat.WEBP to webp, ImageFormat.GIF to gif89)

        for ((format, header) in headers) {
            assertThat(format.matches(header)).describedAs("$format").isTrue()
            headers.filterKeys { it != format }.values.forEach { other -> assertThat(format.matches(other)).describedAs("$format").isFalse() }
            assertThat(format.matches(html)).isFalse()
        }
        assertThat(ImageFormat.GIF.matches(gif87)).isTrue()
    }

    @Test
    @DisplayName("WebP는 RIFF만으로는 부족하다. 8바이트 뒤의 WEBP까지 맞아야 한다")
    fun `webp needs both markers`() {
        val wave = bytes(0x52, 0x49, 0x46, 0x46, 0x24, 0, 0, 0, 0x57, 0x41, 0x56, 0x45)

        assertThat(ImageFormat.WEBP.matches(wave)).isFalse()
        assertThat(ImageFormat.WEBP.matches(webp.copyOf(8))).isFalse()
    }

    @Test
    @DisplayName("시그니처보다 짧은 파일은 어느 형식도 아니다")
    fun `too short`() {
        assertThat(ImageFormat.entries.none { it.matches(bytes(0xFF, 0xD8)) }).isTrue()
        assertThat(ImageFormat.entries.none { it.matches(ByteArray(0)) }).isTrue()
    }

    @Test
    @DisplayName("선언한 형식은 대소문자와 공백을 가리지 않고, 허용하지 않은 형식은 없다")
    fun `declared content types`() {
        assertThat(ImageFormat.of("image/jpeg")).isEqualTo(ImageFormat.JPEG)
        assertThat(ImageFormat.of(" Image/PNG ")).isEqualTo(ImageFormat.PNG)
        assertThat(ImageFormat.of("image/svg+xml")).isNull()
        assertThat(ImageFormat.of("text/html")).isNull()
    }
}
