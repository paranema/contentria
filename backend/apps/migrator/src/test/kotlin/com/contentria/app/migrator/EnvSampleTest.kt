package com.contentria.app.migrator

import com.contentria.platform.architecture.EnvSample
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Path

class EnvSampleTest {

    @Test
    @DisplayName(".env.sample은 main이 읽는 환경 변수를 빠짐없이, 그것만 담는다")
    fun `sample lists what main reads`() {
        val read = setOf(DATABASE_URL_VARIABLE) +
            Migrator.CONTEXTS.flatMap { listOf(it.usernameVariable, it.passwordVariable) }

        assertThat(EnvSample.variablesListedIn(Path.of(".env.sample")))
            .containsExactlyInAnyOrderElementsOf(read)
    }
}
