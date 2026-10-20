package com.contentria.publishing.application.required

/** The readable text of a Markdown body, with the syntax taken out. Used to derive summaries. */
interface MarkdownPlainText {
    fun of(markdown: String): String
}
