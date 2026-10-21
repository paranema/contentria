package com.contentria.notification.application

import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Where a page of notifications ends: the last one's time and id. Opaque to clients. */
internal data class NotificationCursor(val at: Instant, val id: UUID) {

    fun encode(): String = BASE64.encodeToString("${at.epochSecond}.${at.nano}.$id".toByteArray())

    companion object {

        private val BASE64 = Base64.getUrlEncoder().withoutPadding()

        /** After every notification, for the first page. PostgreSQL orders uuids byte by byte. */
        val START = NotificationCursor(Instant.parse("9999-12-31T23:59:59Z"), UUID(-1L, -1L))

        fun decode(raw: String?): NotificationCursor {
            if (raw.isNullOrBlank()) return START
            return runCatching {
                val (seconds, nanos, id) = String(Base64.getUrlDecoder().decode(raw)).split('.')
                NotificationCursor(Instant.ofEpochSecond(seconds.toLong(), nanos.toLong()), UUID.fromString(id))
            }.getOrElse { throw NotificationException.InvalidCursor() }
        }
    }
}
