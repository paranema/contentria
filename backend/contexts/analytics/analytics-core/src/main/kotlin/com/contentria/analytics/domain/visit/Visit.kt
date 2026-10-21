package com.contentria.analytics.domain.visit

import com.contentria.analytics.domain.AnalyticsSchema
import com.contentria.shared.id.Ids
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * One reader opening a blog's home or one of its posts (docs/contexts/analytics/01-rules.md, 흐름 1).
 * Written once and never changed; removed after 90 days. Whether it counts was decided before it
 * was made: here are only the facts kept about it.
 *
 * No IP and no address beyond the referrer's host (01-rules, IP를 남기지 않는다).
 */
@Entity
@Table(name = "visits", schema = AnalyticsSchema.NAME)
class Visit private constructor(
    id: UUID,
    blogId: UUID,
    postId: UUID?,
    visitorKey: String,
    referrerHost: String?,
    visitedAt: Instant,
) {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @Column(name = "blog_id", nullable = false, updatable = false)
    val blogId: UUID = blogId

    /** The post opened; null for the blog's home. */
    @Column(name = "post_id", updatable = false)
    val postId: UUID? = postId

    /** Who, as far as one day goes: the same reader has another key the next day. */
    @Column(name = "visitor_key", nullable = false, updatable = false, length = VISITOR_KEY_LENGTH)
    val visitorKey: String = visitorKey

    @Column(name = "referrer_host", updatable = false, length = HOST_MAX_LENGTH)
    val referrerHost: String? = referrerHost

    @Column(name = "visited_at", nullable = false, updatable = false)
    val visitedAt: Instant = visitedAt

    override fun equals(other: Any?): Boolean =
        this === other || (other is Visit && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {

        /** HMAC-SHA256 in hexadecimal. */
        const val VISITOR_KEY_LENGTH = 64
        const val HOST_MAX_LENGTH = 255

        private val VISITOR_KEY = Regex("^[0-9a-f]{$VISITOR_KEY_LENGTH}$")

        /** 흐름 1의 5번. [referrer] is the page the reader came from, as the browser sent it. */
        fun record(blogId: UUID, postId: UUID?, visitorKey: String, referrer: String?, at: Instant): Visit {
            require(VISITOR_KEY.matches(visitorKey)) { "A visitor key is $VISITOR_KEY_LENGTH lowercase hexadecimal digits" }
            return Visit(Ids.newId(), blogId, postId, visitorKey, hostOf(referrer), at)
        }

        /** 01-rules, 처음 정하는 값: the domain only, never the path or the query. Anything not a web address is dropped. */
        internal fun hostOf(referrer: String?): String? {
            if (referrer.isNullOrBlank()) return null
            val uri = runCatching { URI(referrer.trim()) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
            return uri.host?.lowercase()?.takeIf { it.isNotEmpty() && it.length <= HOST_MAX_LENGTH }
        }
    }
}
