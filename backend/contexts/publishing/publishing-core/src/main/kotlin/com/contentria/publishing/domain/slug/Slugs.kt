package com.contentria.publishing.domain.slug

import java.util.Locale

/**
 * Slugs for posts and categories (docs/contexts/publishing/01-rules.md, 글 slug).
 *
 * Lowercase Latin letters, digits, Hangul syllables and single hyphens between them. Korean is
 * kept as it is: a title in Korean reads better as a Korean address than as a transliteration.
 * Blog slugs follow a stricter rule of their own ([com.contentria.publishing.domain.blog.Blog]).
 *
 * Pure functions. Which slugs are already taken is the use case's question; it passes the answer in.
 */
object Slugs {

    private val NOT_ALLOWED = Regex("[^a-z0-9가-힣\\s-]")
    private val WHITESPACE = Regex("\\s+")
    private val HYPHENS = Regex("-+")
    private val VALID = Regex("^[a-z0-9가-힣]+(-[a-z0-9가-힣]+)*$")

    /**
     * Turns [text] into a slug of at most [maxLength] characters, or [fallback] when nothing usable
     * is left (a title made only of punctuation, for instance).
     */
    fun from(text: String, fallback: String, maxLength: Int): String {
        val slug = text.trim().lowercase(Locale.ROOT)
            .replace(NOT_ALLOWED, "")
            .replace(WHITESPACE, "-")
            .replace(HYPHENS, "-")
            .trim('-')
            .take(maxLength)
            .trimEnd('-')
        return slug.ifEmpty { fallback }
    }

    /** Whether [slug] is one an author may type in directly. */
    fun isValid(slug: String, maxLength: Int): Boolean = slug.length <= maxLength && VALID.matches(slug)

    /**
     * [base] itself if nobody has it, otherwise `base-2`, `base-3` and so on. The base is shortened
     * when needed so the suffixed slug still fits in [maxLength].
     */
    fun nextAvailable(base: String, taken: Set<String>, maxLength: Int): String {
        if (base !in taken) return base
        var n = 2
        while (true) {
            val suffix = "-$n"
            val candidate = base.take(maxLength - suffix.length).trimEnd('-') + suffix
            if (candidate !in taken) return candidate
            n++
        }
    }
}
