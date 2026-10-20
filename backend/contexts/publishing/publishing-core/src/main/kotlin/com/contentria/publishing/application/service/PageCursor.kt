package com.contentria.publishing.application.service

import com.contentria.publishing.application.provided.PostListItem
import com.contentria.publishing.application.provided.PostPage
import com.contentria.publishing.application.provided.PublishingUseCaseException
import com.contentria.publishing.application.required.PostRow
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Where a page of a list ends: the last row's time and id. Opaque to clients, who only hand it back.
 *
 * Keyset rather than offset: a post published while someone pages through does not shift what
 * the next page shows, and a deep page costs what the first one does.
 */
internal data class PageCursor(val at: Instant, val id: UUID) {

    fun encode(): String = BASE64.encodeToString("${at.epochSecond}.${at.nano}.$id".toByteArray())

    companion object {

        private val BASE64 = Base64.getUrlEncoder().withoutPadding()

        /** Before everything, for the first page. PostgreSQL's timestamp range ends in the year 294276. */
        val START = PageCursor(Instant.parse("9999-12-31T23:59:59Z"), UUID(-1L, -1L))

        fun decode(raw: String?): PageCursor {
            if (raw.isNullOrBlank()) return START
            return runCatching {
                val (seconds, nanos, id) = String(Base64.getUrlDecoder().decode(raw)).split('.')
                PageCursor(Instant.ofEpochSecond(seconds.toLong(), nanos.toLong()), UUID.fromString(id))
            }.getOrElse { throw PublishingUseCaseException.InvalidCursor() }
        }

        /** Rows come one beyond a page; the extra one only says that another page exists. */
        fun page(rows: List<PostRow>, keyOf: (PostRow) -> Instant): PostPage {
            val shown = rows.take(PostPage.SIZE)
            val next = if (rows.size > PostPage.SIZE) shown.last().let { PageCursor(keyOf(it), it.postId).encode() } else null
            return PostPage(shown.map { it.item() }, next)
        }

        private fun PostRow.item() =
            PostListItem(postId, blogSlug, categoryId, title, summary, status, publishedAt, slug, updatedAt)
    }
}
