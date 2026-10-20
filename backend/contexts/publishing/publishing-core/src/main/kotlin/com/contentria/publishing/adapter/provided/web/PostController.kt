package com.contentria.publishing.adapter.provided.web

import com.contentria.publishing.application.provided.ChangePostSlugUseCase
import com.contentria.publishing.application.provided.ChangePostStatusUseCase
import com.contentria.publishing.application.provided.DeletePostUseCase
import com.contentria.publishing.application.provided.GetMyPostsQuery
import com.contentria.publishing.application.provided.PostListItem
import com.contentria.publishing.application.provided.PostView
import com.contentria.publishing.application.provided.RevisePostCommand
import com.contentria.publishing.application.provided.RevisePostUseCase
import com.contentria.publishing.application.provided.WritePostCommand
import com.contentria.publishing.application.provided.WritePostUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * docs/contexts/publishing/01-rules.md, 흐름 3~5와 글 slug: the owner's side. Posts are addressed
 * by id here; readers find them by blog and slug (P4c).
 */
@RestController
class PostController(
    private val writePost: WritePostUseCase,
    private val revisePost: RevisePostUseCase,
    private val changeSlug: ChangePostSlugUseCase,
    private val changeStatus: ChangePostStatusUseCase,
    private val deletePost: DeletePostUseCase,
    private val myPosts: GetMyPostsQuery,
) {

    @PostMapping("/blogs/{slug}/posts")
    fun write(@AuthenticationPrincipal accountId: UUID, @PathVariable slug: String, @Valid @RequestBody request: PostRequest): ResponseEntity<PostView> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(writePost.write(WritePostCommand(accountId, slug, request.title, request.body, request.summary, request.categoryId)))

    @PutMapping("/posts/{id}")
    fun revise(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID, @Valid @RequestBody request: PostRequest): PostView =
        revisePost.revise(RevisePostCommand(accountId, id, request.title, request.body, request.summary, request.categoryId))

    @PutMapping("/posts/{id}/slug")
    fun changeSlug(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID, @Valid @RequestBody request: ChangePostSlugRequest): PostView =
        changeSlug.changeSlug(accountId, id, request.slug)

    @PostMapping("/posts/{id}/publish")
    fun publish(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID): PostView = changeStatus.publish(accountId, id)

    @PostMapping("/posts/{id}/hide")
    fun hide(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID): PostView = changeStatus.hide(accountId, id)

    @PostMapping("/posts/{id}/republish")
    fun republish(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID): PostView = changeStatus.republish(accountId, id)

    @DeleteMapping("/posts/{id}")
    fun delete(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID): ResponseEntity<Void> {
        deletePost.delete(accountId, id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me/posts/{id}")
    fun mine(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID): PostView = myPosts.get(accountId, id)

    @GetMapping("/me/blogs/{slug}/posts")
    fun myList(@AuthenticationPrincipal accountId: UUID, @PathVariable slug: String): List<PostListItem> = myPosts.list(accountId, slug)
}
