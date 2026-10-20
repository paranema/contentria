package com.contentria.interaction.application

import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Where a page of a post's comments ends: the last top-level comment's time and id. Opaque to clients. */
internal data class CommentCursor(val at: Instant, val id: UUID) {

    fun encode(): String = BASE64.encodeToString("${at.epochSecond}.${at.nano}.$id".toByteArray())

    companion object {

        private val BASE64 = Base64.getUrlEncoder().withoutPadding()

        /** Before every comment, for the first page. */
        val START = CommentCursor(Instant.EPOCH, UUID(0L, 0L))

        fun decode(raw: String?): CommentCursor {
            if (raw.isNullOrBlank()) return START
            return runCatching {
                val (seconds, nanos, id) = String(Base64.getUrlDecoder().decode(raw)).split('.')
                CommentCursor(Instant.ofEpochSecond(seconds.toLong(), nanos.toLong()), UUID.fromString(id))
            }.getOrElse { throw InteractionException.InvalidCursor() }
        }
    }
}
