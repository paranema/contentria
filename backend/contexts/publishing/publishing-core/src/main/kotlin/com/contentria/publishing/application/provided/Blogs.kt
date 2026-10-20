package com.contentria.publishing.application.provided

import java.util.UUID

/** docs/contexts/publishing/01-rules.md, 흐름 1. 블로그 만들기 */
interface CreateBlogUseCase {
    /** Creates the blog with its sample categories and sample drafts. */
    fun create(command: CreateBlogCommand): BlogView
}

data class CreateBlogCommand(val ownerId: UUID, val slug: String, val title: String, val description: String?)

interface ChangeBlogSettingsUseCase {
    fun change(command: ChangeBlogSettingsCommand): BlogView
}

data class ChangeBlogSettingsCommand(val requesterId: UUID, val blogSlug: String, val title: String, val description: String?)

interface GetBlogQuery {
    fun getBySlug(slug: String): BlogView?

    /** The owner's blogs, oldest first. */
    fun getOwnedBy(ownerId: UUID): List<BlogView>
}

data class BlogView(val blogId: UUID, val ownerId: UUID, val slug: String, val title: String, val description: String?)
