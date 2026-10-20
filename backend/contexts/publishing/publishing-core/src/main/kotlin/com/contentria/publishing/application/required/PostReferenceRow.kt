package com.contentria.publishing.application.required

import com.contentria.publishing.domain.post.PostStatus
import java.util.UUID

/** A post's place and status, read without its body. Filled from [com.contentria.publishing.domain.post.Post]'s named query. */
interface PostReferenceRow {
    val postId: UUID
    val blogId: UUID
    val ownerId: UUID
    val status: PostStatus
}
