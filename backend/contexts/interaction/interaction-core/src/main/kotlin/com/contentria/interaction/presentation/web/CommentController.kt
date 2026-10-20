package com.contentria.interaction.presentation.web

import com.contentria.interaction.application.CommentPage
import com.contentria.interaction.application.CommentService
import com.contentria.interaction.application.CommentView
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * docs/contexts/interaction/01-rules.md, 흐름 3과 4. Reading is open to anyone who can see the
 * post; the host's filter chain allows `GET /posts/{id}/comments`.
 */
@RestController
class CommentController(private val comments: CommentService) {

    @GetMapping("/posts/{id}/comments")
    fun comments(@PathVariable id: UUID, @RequestParam(required = false) cursor: String?): CommentPage =
        comments.comments(id, cursor)

    @PostMapping("/posts/{id}/comments")
    fun write(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID, @RequestBody request: WriteCommentRequest): ResponseEntity<CommentView> =
        ResponseEntity.status(HttpStatus.CREATED).body(comments.write(accountId, id, request.body, request.parentId))

    @PatchMapping("/comments/{id}")
    fun edit(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID, @RequestBody request: EditCommentRequest): CommentView =
        comments.edit(accountId, id, request.body)

    @DeleteMapping("/comments/{id}")
    fun delete(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID): ResponseEntity<Void> {
        comments.delete(accountId, id)
        return ResponseEntity.noContent().build()
    }
}

/** A reply when [parentId] is given. */
data class WriteCommentRequest(val body: String, val parentId: UUID? = null)

data class EditCommentRequest(val body: String)
