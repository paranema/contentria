package com.contentria.member.domain.profile

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ProfileTest {

    private val accountId: UUID = UUID.fromString("0197f8a0-0000-7000-8000-000000000001")

    private fun profile(
        displayName: String = "행복한_다람쥐_1234",
        pictureUrl: String? = null,
    ) = Profile.create(accountId, displayName, pictureUrl)

    @Nested
    @DisplayName("식별자")
    inner class Identity {

        @Test
        @DisplayName("프로필의 식별자는 계정 식별자와 같다. 다른 컨텍스트가 들고 있는 id로 바로 조회할 수 있다")
        fun `shares the account identifier`() {
            val profile = profile()

            assertThat(profile.id).isEqualTo(accountId)
            assertThat(profile.accountId).isEqualTo(accountId)
        }

        @Test
        @DisplayName("같은 계정의 프로필은 동일하게 취급된다")
        fun `equality is by identifier`() {
            assertThat(profile(displayName = "이름_하나_1111"))
                .isEqualTo(profile(displayName = "이름_둘_2222"))
        }
    }

    @Nested
    @DisplayName("표시명")
    inner class DisplayName_ {

        @Test
        @DisplayName("앞뒤 공백을 제거하고 저장한다")
        fun `trims surrounding whitespace`() {
            assertThat(profile(displayName = "  행복한_다람쥐_1234  ").displayName)
                .isEqualTo("행복한_다람쥐_1234")
        }

        @Test
        @DisplayName("최소 길이 미만은 거부한다")
        fun `rejects a name shorter than the minimum`() {
            assertThatThrownBy { profile(displayName = "가") }
                .isInstanceOf(ProfileException.InvalidDisplayName::class.java)
        }

        @Test
        @DisplayName("공백만 있는 표시명은 거부한다")
        fun `rejects a blank name`() {
            assertThatThrownBy { profile(displayName = "      ") }
                .isInstanceOf(ProfileException.InvalidDisplayName::class.java)
        }

        @Test
        @DisplayName("컬럼 길이까지는 허용하고 한 글자라도 넘으면 거부한다")
        fun `accepts up to the column length and rejects beyond it`() {
            val atLimit = "가".repeat(Profile.DISPLAY_NAME_MAX_LENGTH)
            val overLimit = "가".repeat(Profile.DISPLAY_NAME_MAX_LENGTH + 1)

            assertThatCode { profile(displayName = atLimit) }.doesNotThrowAnyException()
            assertThatThrownBy { profile(displayName = overLimit) }
                .isInstanceOf(ProfileException.InvalidDisplayName::class.java)
        }

        @Test
        @DisplayName("이름 변경도 같은 정규화와 검증을 거친다")
        fun `rename applies the same rules`() {
            val profile = profile()

            profile.rename("  새로운_이름_9999 ")
            assertThat(profile.displayName).isEqualTo("새로운_이름_9999")

            assertThatThrownBy { profile.rename("가") }
                .isInstanceOf(ProfileException.InvalidDisplayName::class.java)
            assertThat(profile.displayName).isEqualTo("새로운_이름_9999")
        }
    }

    @Nested
    @DisplayName("프로필 이미지")
    inner class PictureUrl {

        @Test
        @DisplayName("없음과 빈 문자열을 모두 null로 저장한다. 읽는 쪽은 한 가지만 검사하면 된다")
        fun `absent and blank are both stored as null`() {
            assertThat(profile(pictureUrl = null).pictureUrl).isNull()
            assertThat(profile(pictureUrl = "").pictureUrl).isNull()
            assertThat(profile(pictureUrl = "   ").pictureUrl).isNull()
        }

        @Test
        @DisplayName("URL의 앞뒤 공백은 제거한다")
        fun `trims the url`() {
            assertThat(profile(pictureUrl = " https://cdn.example.com/a.png ").pictureUrl)
                .isEqualTo("https://cdn.example.com/a.png")
        }

        @Test
        @DisplayName("컬럼 길이를 넘는 URL은 조용히 버리지 않고 거부한다")
        fun `an over-long url is rejected, not silently dropped`() {
            val tooLong = "h".repeat(Profile.PICTURE_URL_MAX_LENGTH + 1)

            assertThatThrownBy { profile(pictureUrl = tooLong) }
                .isInstanceOf(ProfileException.InvalidPictureUrl::class.java)
        }

        @Test
        @DisplayName("이미지를 교체하거나 제거할 수 있다")
        fun `the picture can be replaced or cleared`() {
            val profile = profile(pictureUrl = "https://cdn.example.com/a.png")

            profile.changePicture("https://cdn.example.com/b.png")
            assertThat(profile.pictureUrl).isEqualTo("https://cdn.example.com/b.png")

            profile.changePicture(null)
            assertThat(profile.pictureUrl).isNull()
        }
    }

    @Nested
    @DisplayName("탈퇴 표시")
    inner class Withdrawal {

        private val at = Instant.parse("2026-10-19T00:00:00Z")

        @Test
        @DisplayName("탈퇴해도 이름과 사진은 남고 탈퇴 시각만 붙는다")
        fun `keeps the name and picture`() {
            val profile = Profile.create(UUID.randomUUID(), "남은 이름", "https://img.contentria.com/a.png")

            profile.markWithdrawn(at)

            assertThat(profile.isWithdrawn).isTrue()
            assertThat(profile.withdrawnAt).isEqualTo(at)
            assertThat(profile.displayName).isEqualTo("남은 이름")
            assertThat(profile.pictureUrl).isEqualTo("https://img.contentria.com/a.png")
        }

        @Test
        @DisplayName("두 번 표시해도 처음 시각을 유지한다")
        fun `marking twice keeps the first time`() {
            val profile = Profile.create(UUID.randomUUID(), "남은 이름")
            profile.markWithdrawn(at)
            profile.markWithdrawn(at.plusSeconds(60))

            assertThat(profile.withdrawnAt).isEqualTo(at)
        }
    }
}
