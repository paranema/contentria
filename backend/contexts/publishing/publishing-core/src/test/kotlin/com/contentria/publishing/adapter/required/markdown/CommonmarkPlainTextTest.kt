package com.contentria.publishing.adapter.required.markdown

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CommonmarkPlainTextTest {

    private val plainText = CommonmarkPlainText()

    @Test
    @DisplayName("마크다운 문법과 링크 주소를 걷어 내고 읽히는 글자만 한 줄로 남긴다")
    fun `keeps only the words a reader sees`() {
        val markdown = """
            # 제목

            **굵게**, _기울임_, ~~취소선~~ 그리고 [링크](https://example.com).

            - 목록 하나
            - 목록 둘
        """.trimIndent()

        assertThat(plainText.of(markdown)).isEqualTo("제목 굵게, 기울임, 취소선 그리고 링크. 목록 하나 목록 둘")
    }

    @Test
    @DisplayName("이미지, 코드 블록, HTML은 요약에 넣지 않는다. 인라인 코드는 넣는다")
    fun `leaves out what is not prose`() {
        val markdown = """
            ![사진 설명](https://img.example.com/a.png) 첫 문장은 `val x`로 시작한다.

            ```kotlin
            fun hidden() = Unit
            ```

            <div>숨긴 HTML</div>

            마지막 문장.
        """.trimIndent()

        assertThat(plainText.of(markdown)).isEqualTo("첫 문장은 val x로 시작한다. 마지막 문장.")
    }

    @Test
    @DisplayName("표의 칸 사이를 띄운다")
    fun `table cells are separated`() {
        assertThat(plainText.of("| 책 | 지은이 |\n|---|---|\n| 어린 왕자 | 생텍쥐페리 |")).isEqualTo("책 지은이 어린 왕자 생텍쥐페리")
    }

    @Test
    @DisplayName("빈 본문은 빈 글이다")
    fun `empty body`() {
        assertThat(plainText.of("")).isEmpty()
    }
}
