package com.contentria.publishing.application.required

import com.contentria.publishing.api.event.PostDeleted
import com.contentria.publishing.api.event.PostPublished

/**
 * Events other contexts learn about posts from (docs/contexts/publishing/02-implementation.md,
 * 이벤트). Recorded in the transaction that makes the change: if it rolls back, the event is gone too.
 */
interface PostEvents {
    fun published(event: PostPublished)
    fun deleted(event: PostDeleted)
}
