package com.contentria.publishing.api.event

import java.time.Instant
import java.util.UUID

/** Topic names: `<context>.<aggregate>` (docs/architecture/03-context-integration.md, 토픽). */
object PublishingTopics {
    const val POST = "publishing.post"
}

/**
 * A post was published for the first time; republishing a private post is not one
 * (docs/contexts/publishing/01-rules.md, 발행 알림). Carries the blog's owner so that a consumer
 * does not have to ask publishing whose blog it is.
 */
data class PostPublished(val postId: UUID, val blogId: UUID, val ownerId: UUID, val publishedAt: Instant) {
    companion object {
        const val TYPE = "PostPublished"
        const val VERSION = 1
    }
}

/** A post was deleted, with its slugs. Interaction removes its comments and likes. */
data class PostDeleted(val postId: UUID, val blogId: UUID) {
    companion object {
        const val TYPE = "PostDeleted"
        const val VERSION = 1
    }
}
