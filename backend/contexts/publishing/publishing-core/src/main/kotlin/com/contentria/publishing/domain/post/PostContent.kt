package com.contentria.publishing.domain.post

/** What the author writes. Validated when the post takes it. */
data class PostContent(val title: String, val body: String, val summary: PostSummary)
