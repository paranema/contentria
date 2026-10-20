package com.contentria.publishing.domain.post

/** docs/contexts/publishing/01-rules.md, 흐름 4 */
enum class PostStatus {

    /** Never published. Only the owner sees it, and its slug still follows the title. */
    DRAFT,

    PUBLISHED,

    /** Published once, now hidden again. Only the owner sees it. */
    PRIVATE;

    val isPublic: Boolean get() = this == PUBLISHED
}
