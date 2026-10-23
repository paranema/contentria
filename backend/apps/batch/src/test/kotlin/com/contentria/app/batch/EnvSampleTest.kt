package com.contentria.app.batch

import com.contentria.platform.architecture.EnvSample
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Path

class EnvSampleTest {

    @Test
    @DisplayName(".env.sample은 application.yml이 읽는 환경 변수를 빠짐없이, 그것만 담는다")
    fun `sample lists what the configuration reads`() {
        assertThat(EnvSample.variablesListedIn(Path.of(".env.sample")))
            .containsExactlyInAnyOrderElementsOf(EnvSample.variablesReadBy(Path.of("src/main/resources/application.yml")))
    }
}
