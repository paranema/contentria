package com.contentria.member.domain.profile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Random

class DisplayNameGeneratorTest {

    @Test
    @DisplayName("형용사_명사_네자리숫자 형식으로 만든다")
    fun `produces adjective_noun_number`() {
        val generator = DisplayNameGenerator(Random(42))

        assertThat(generator.generate()).matches("""^\S+_\S+_\d{4}$""")
    }

    @Test
    @DisplayName("같은 시드는 같은 결과를 낸다. 무작위성은 주입된 Random에서만 온다")
    fun `is deterministic for a given seed`() {
        val first = DisplayNameGenerator(Random(42)).generate()
        val second = DisplayNameGenerator(Random(42)).generate()

        assertThat(first).isEqualTo(second)
    }

    @Test
    @DisplayName("숫자 접미사는 항상 네 자리다")
    fun `the numeric suffix always has four digits`() {
        val generator = DisplayNameGenerator(Random(7))

        repeat(500) {
            val suffix = generator.generate().substringAfterLast('_').toInt()
            assertThat(suffix).isBetween(1000, 9999)
        }
    }

    @Test
    @DisplayName("생성한 표시명은 Profile이 받아들이는 길이 안에 있다")
    fun `every generated name fits what Profile accepts`() {
        val generator = DisplayNameGenerator(Random(11))

        repeat(500) {
            assertThat(generator.generate().length)
                .isBetween(Profile.DISPLAY_NAME_MIN_LENGTH, Profile.DISPLAY_NAME_MAX_LENGTH)
        }
    }

    @Test
    @DisplayName("유일성을 보장하지는 않는다. 중복 처리는 호출자의 재시도 책임이다")
    fun `does not guarantee uniqueness`() {
        val generator = DisplayNameGenerator(Random(3))
        val generated = List(2_000) { generator.generate() }

        assertThat(generated).isNotEmpty()
        assertThat(generated.toSet().size).isLessThanOrEqualTo(generated.size)
    }
}
