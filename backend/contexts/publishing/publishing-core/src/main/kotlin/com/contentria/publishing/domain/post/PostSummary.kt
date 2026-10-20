package com.contentria.publishing.domain.post

/**
 * The text shown in post lists. Either the author wrote it, or the use case derived it from the
 * body; the domain knows which, so a derived summary can follow the body when it changes.
 *
 * Deriving it needs a Markdown parser, which the domain cannot depend on
 * (docs/contexts/publishing/02-implementation.md, 규칙과 도메인 메서드).
 */
class PostSummary private constructor(val text: String, val writtenByAuthor: Boolean) {

    companion object {
        const val MAX_LENGTH = 300

        /** What the use case cuts a derived summary to. */
        const val DERIVED_LENGTH = 150

        fun written(text: String): PostSummary = PostSummary(validated(text.trim()), writtenByAuthor = true)

        fun derived(text: String): PostSummary = PostSummary(validated(text.trim()), writtenByAuthor = false)

        private fun validated(text: String): String {
            if (text.length > MAX_LENGTH) throw PostException.InvalidSummary(text.length)
            return text
        }
    }
}
