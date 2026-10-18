package com.contentria.member.domain.profile

import java.security.SecureRandom
import java.util.Random

/**
 * Produces a display name for accounts that arrive without a usable one — every email
 * sign-up, and any social provider that returns no name.
 *
 * Uniqueness is not this class's job. It cannot be: checking a candidate and then inserting
 * it is a race two concurrent sign-ups will lose. The caller retries with a fresh candidate
 * when the unique constraint on `profiles.display_name` rejects one.
 *
 * Plain class rather than a Spring bean so it stays callable from a unit test with a seeded
 * [Random]; the application module registers the instance.
 */
class DisplayNameGenerator(
    private val random: Random = SecureRandom(),
) {

    fun generate(): String {
        val adjective = ADJECTIVES[random.nextInt(ADJECTIVES.size)]
        val noun = NOUNS[random.nextInt(NOUNS.size)]
        val suffix = random.nextInt(9000) + 1000
        return "${adjective}_${noun}_$suffix"
    }

    private companion object {

        val ADJECTIVES = listOf(
            "행복한", "즐거운", "멋진", "귀여운", "배고픈", "용감한", "상냥한", "친절한",
            "똑똑한", "빛나는", "차가운", "따뜻한", "고요한", "활기찬", "신비로운", "평화로운",
            "명랑한", "순수한", "소박한", "거대한", "작은", "빠른", "느린", "강력한", "온화한",
        )

        val NOUNS = listOf(
            "다람쥐", "고양이", "강아지", "호랑이", "판다", "여우", "토끼", "부엉이",
            "돌고래", "햄스터", "코알라", "북극곰", "펭귄", "사자", "코끼리", "기린",
            "물범", "알파카", "수달", "라쿤", "쿼카", "나무늘보", "고슴도치", "두더지", "비둘기",
        )
    }
}
