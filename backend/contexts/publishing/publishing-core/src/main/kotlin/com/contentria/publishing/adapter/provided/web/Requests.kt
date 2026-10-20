package com.contentria.publishing.adapter.provided.web

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

// Sizes here only stop absurd input early. The rules live in the domain (01-rules, 처음 정하는 값).

data class CreateBlogRequest(
    @field:NotBlank @field:Size(max = 100) val slug: String,
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:Size(max = 1000) val description: String? = null,
)

data class ChangeBlogSettingsRequest(
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:Size(max = 1000) val description: String? = null,
)

data class EditCategoryTreeRequest(
    @field:NotNull val version: Long,
    @field:Size(max = 200) @field:Valid val categories: List<CategoryNodeRequest>,
)

/** [key] is an existing category's id, or any temporary key for a new one; [parentKey] names another node's key. */
data class CategoryNodeRequest(
    @field:NotBlank @field:Size(max = 100) val key: String,
    @field:Size(max = 200) val name: String,
    @field:Size(max = 100) val parentKey: String? = null,
)

data class ErrorResponse(val code: String, val message: String)
