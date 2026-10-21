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

/** Sizes stop absurd input; the post's own limits are the domain's (01-rules, 처음 정하는 값). */
data class PostRequest(
    @field:NotNull @field:Size(max = 1000) val title: String,
    @field:NotNull @field:Size(max = 200_000) val body: String,
    @field:Size(max = 2000) val summary: String? = null,
    val categoryId: java.util.UUID? = null,
    /** The video to attach, or null for none (01-rules, 첨부 영상). */
    val videoId: java.util.UUID? = null,
)

/** Null or blank: make the slug from the title again. */
data class ChangePostSlugRequest(@field:Size(max = 200) val slug: String? = null)

data class ErrorResponse(val code: String, val message: String)
