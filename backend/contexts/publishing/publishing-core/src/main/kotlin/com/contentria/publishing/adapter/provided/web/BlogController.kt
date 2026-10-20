package com.contentria.publishing.adapter.provided.web

import com.contentria.publishing.application.provided.BlogView
import com.contentria.publishing.application.provided.ChangeBlogSettingsCommand
import com.contentria.publishing.application.provided.ChangeBlogSettingsUseCase
import com.contentria.publishing.application.provided.CreateBlogCommand
import com.contentria.publishing.application.provided.CreateBlogUseCase
import com.contentria.publishing.application.provided.GetBlogQuery
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** docs/contexts/publishing/01-rules.md, 흐름 1. The principal is the account id put there by the host app. */
@RestController
class BlogController(
    private val createBlog: CreateBlogUseCase,
    private val changeSettings: ChangeBlogSettingsUseCase,
    private val blogs: GetBlogQuery,
) {

    @PostMapping("/blogs")
    fun create(@AuthenticationPrincipal accountId: UUID, @Valid @RequestBody request: CreateBlogRequest): ResponseEntity<BlogView> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(createBlog.create(CreateBlogCommand(accountId, request.slug, request.title, request.description)))

    @GetMapping("/me/blogs")
    fun mine(@AuthenticationPrincipal accountId: UUID): List<BlogView> = blogs.getOwnedBy(accountId)

    @GetMapping("/blogs/{slug}")
    fun get(@PathVariable slug: String): ResponseEntity<BlogView> =
        blogs.getBySlug(slug)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @PatchMapping("/blogs/{slug}")
    fun changeSettings(
        @AuthenticationPrincipal accountId: UUID,
        @PathVariable slug: String,
        @Valid @RequestBody request: ChangeBlogSettingsRequest,
    ): BlogView = changeSettings.change(ChangeBlogSettingsCommand(accountId, slug, request.title, request.description))
}
