package com.contentria.publishing.adapter.provided.web

import com.contentria.publishing.application.provided.CategoryTreeView
import com.contentria.publishing.application.provided.EditCategoryTreeCommand
import com.contentria.publishing.application.provided.EditCategoryTreeUseCase
import com.contentria.publishing.application.provided.GetCategoryTreeQuery
import com.contentria.publishing.domain.category.CategoryTreeEdit
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * docs/contexts/publishing/01-rules.md, 흐름 2. The editor loads the tree with its version, edits
 * it, and puts the whole tree back with that version.
 */
@RestController
@RequestMapping("/blogs/{slug}/categories")
class CategoryController(
    private val trees: GetCategoryTreeQuery,
    private val editTree: EditCategoryTreeUseCase,
) {

    @GetMapping
    fun get(@PathVariable slug: String): ResponseEntity<CategoryTreeView> =
        trees.get(slug)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @PutMapping
    fun replace(
        @AuthenticationPrincipal accountId: UUID,
        @PathVariable slug: String,
        @Valid @RequestBody request: EditCategoryTreeRequest,
    ): CategoryTreeView = editTree.edit(
        EditCategoryTreeCommand(
            requesterId = accountId,
            blogSlug = slug,
            expectedVersion = request.version,
            nodes = request.categories.map { CategoryTreeEdit.Node(it.key, it.name, it.parentKey) },
        ),
    )
}
