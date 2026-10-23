package com.contentria.platform.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class EnvSampleTest {

    @TempDir
    lateinit var dir: Path

    @Test
    @DisplayName("샘플은 값이 있는 줄과 주석 처리한 기본값 줄을 모두 목록으로 센다. 설명 주석은 세지 않는다")
    fun `sample lines`() {
        val sample = dir.resolve(".env.sample").apply {
            writeText(
                """
                # [필수][비밀] 설명. KEY=처럼 비워 두면 빈 문자열이 된다
                DATABASE_PASSWORD=
                # DATABASE_URL=jdbc:postgresql://localhost:5432/contentria_v2
                #   Docker: docker run --env-file .env
                MEMBER_COOKIE_SECURE=false
                """.trimIndent(),
            )
        }

        assertThat(EnvSample.variablesListedIn(sample))
            .containsExactlyInAnyOrder("DATABASE_PASSWORD", "DATABASE_URL", "MEMBER_COOKIE_SECURE")
    }

    @Test
    @DisplayName("설정은 기본값이 있든 없든 환경 변수 자리만 센다. 속성 참조와 {baseUrl} 같은 자리는 세지 않는다")
    fun `configuration placeholders`() {
        val config = dir.resolve("application.yml").apply {
            writeText(
                """
                url: ${'$'}{DATABASE_URL:jdbc:postgresql://localhost:5432/contentria_v2}
                password: ${'$'}{DATABASE_PASSWORD}
                redirect-uri: ${'$'}{GOOGLE_REDIRECT_URI:{baseUrl}/login/oauth2/code/{registrationId}}
                interval: ${'$'}{contentria.media.worker.poll-interval:5s}
                """.trimIndent(),
            )
        }

        assertThat(EnvSample.variablesReadBy(config))
            .containsExactlyInAnyOrder("DATABASE_URL", "DATABASE_PASSWORD", "GOOGLE_REDIRECT_URI")
    }
}
