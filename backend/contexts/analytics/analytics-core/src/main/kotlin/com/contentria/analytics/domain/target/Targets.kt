package com.contentria.analytics.domain.target

import java.util.UUID

/** A blog as analytics sees it: only who owns it, to leave the owner's own visits out. */
data class TargetBlog(val blogId: UUID, val ownerId: UUID)

/** A post readers can see, and the blog it sits in. Drafts and private posts never become one. */
data class TargetPost(val postId: UUID, val blogId: UUID)
