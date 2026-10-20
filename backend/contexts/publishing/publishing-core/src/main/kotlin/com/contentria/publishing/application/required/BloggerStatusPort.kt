package com.contentria.publishing.application.required

import java.util.UUID

/**
 * Whether a blogger may write (docs/contexts/publishing/01-rules.md, 전제 6). Publishing's own
 * question in publishing's words; it does not know the member context answers it.
 */
interface BloggerStatusPort {

    /** False for an account that is not active, and for one the member context does not know. */
    fun canPublish(bloggerId: UUID): Boolean
}
