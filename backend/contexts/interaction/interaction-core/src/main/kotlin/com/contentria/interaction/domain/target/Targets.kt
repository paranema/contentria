package com.contentria.interaction.domain.target

import java.util.UUID

/**
 * A post as interaction sees it: where it sits and who owns it. Only published posts become
 * targets; the translation layer hands back nothing for the rest (01-rules 전제 4).
 */
data class TargetPost(val postId: UUID, val blogId: UUID, val ownerId: UUID)

/** A blog as interaction sees it. [slug] is its current address, for lists that link to it. */
data class TargetBlog(val blogId: UUID, val ownerId: UUID, val slug: String)
