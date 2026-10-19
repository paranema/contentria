package com.contentria.member.domain.account

/**
 * The one email normalisation rule of docs/contexts/member/01-rules.md (공통: 이메일 정규화),
 * shared by everything that stores or compares an address: accounts, sign-up attempts, and
 * automatic linking.
 */
object EmailAddresses {

    const val MAX_LENGTH = 255

    /**
     * Deliberately loose. Anything stricter rejects addresses that are valid in practice,
     * and the only proof that an address works is a verification mail reaching it.
     */
    private val PATTERN = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    /**
     * Lower-cases and trims before validating, so `A@x.com` and `a@x.com` cannot both be
     * registered. Dots and `+` suffixes are kept. A unique constraint on an address column
     * only helps if the stored form is canonical.
     */
    fun normalize(raw: String): String {
        val normalized = raw.trim().lowercase()
        if (normalized.length > MAX_LENGTH || !PATTERN.matches(normalized)) {
            throw AccountException.InvalidEmail(raw)
        }
        return normalized
    }
}
