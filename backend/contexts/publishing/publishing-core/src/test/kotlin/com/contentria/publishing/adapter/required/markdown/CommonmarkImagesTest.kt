package com.contentria.publishing.adapter.required.markdown

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CommonmarkImagesTest {

    private val images = CommonmarkImages()

    @Test
    @DisplayName("본문의 이미지를 순서대로, 반복까지 그대로 찾는다. 참조형, 링크 안, 표 안의 이미지도 이미지다")
    fun `every image node`() {
        val body = """
            # 제목
            ![첫](https://img.test/images/a.png) 그리고 ![다른 사이트](https://elsewhere.test/b.jpg)

            [![링크 안](https://img.test/images/c.gif)](https://link.test)

            | 칸 |
            |---|
            | ![표 안](https://img.test/images/d.webp) |

            ![참조형][ref]
            ![첫 다시](https://img.test/images/a.png)

            [ref]: https://img.test/images/e.png
        """.trimIndent()

        assertThat(images.urlsIn(body)).containsExactly(
            "https://img.test/images/a.png",
            "https://elsewhere.test/b.jpg",
            "https://img.test/images/c.gif",
            "https://img.test/images/d.webp",
            "https://img.test/images/e.png",
            "https://img.test/images/a.png",
        )
    }

    @Test
    @DisplayName("코드 안의 이미지 문법, 일반 링크, HTML img는 이미지로 세지 않는다")
    fun `nothing that only looks like one`() {
        val body = """
            `![인라인 코드](https://img.test/images/x.png)` 와 [링크](https://img.test/images/y.png)

            ```
            ![코드 블록](https://img.test/images/z.png)
            ```

            <img src="https://img.test/images/h.png">
        """.trimIndent()

        assertThat(images.urlsIn(body)).isEmpty()
        assertThat(images.urlsIn("")).isEmpty()
    }
}
