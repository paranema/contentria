package com.contentria.publishing.application.provided

import com.contentria.publishing.domain.category.CategoryTreeEdit
import java.util.UUID

/** docs/contexts/publishing/01-rules.md, 흐름 2. 카테고리 편집 */
interface GetCategoryTreeQuery {
    fun get(blogSlug: String): CategoryTreeView?
}

interface EditCategoryTreeUseCase {
    /** Replaces the whole tree. [EditCategoryTreeCommand.expectedVersion] is the version the editor loaded. */
    fun edit(command: EditCategoryTreeCommand): CategoryTreeView
}

data class EditCategoryTreeCommand(
    val requesterId: UUID,
    val blogSlug: String,
    val expectedVersion: Long,
    val nodes: List<CategoryTreeEdit.Node>,
)

/** Top-level categories, each followed by its children, in display order. */
data class CategoryTreeView(val blogId: UUID, val version: Long, val categories: List<CategoryView>)

data class CategoryView(val id: UUID, val name: String, val slug: String, val parentId: UUID?, val displayOrder: Int)
