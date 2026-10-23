package com.contentria.publishing.adapter.provided.web

import com.contentria.publishing.application.provided.GetPublishedPostsQuery
import com.contentria.publishing.application.provided.PostPage
import com.contentria.publishing.application.provided.PublishedPostLookup
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

/**
 * What readers see, signed in or not. Under `GET /blogs/` and `GET /posts`, which the host's
 * filter chain leaves open; nothing here returns a draft or a private post.
 */
@RestController
class PublicPostController(private val published: GetPublishedPostsQuery) {

    @GetMapping("/blogs/{slug}/posts")
    fun inBlog(
        @PathVariable slug: String,
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "0") page: Int,
    ): PostPage = published.inBlog(slug, category, page)

    /** `?ids=a,b`: up to 100 posts; only published ones answer. */
    @GetMapping("/posts/titles")
    fun titles(@RequestParam ids: Set<UUID>): ResponseEntity<Any> {
        if (ids.size > MAX_IDS) return ResponseEntity.badRequest().body(ErrorResponse("INVALID_REQUEST", "Ask for at most $MAX_IDS posts at once."))
        return ResponseEntity.ok(published.titles(ids))
    }

    @GetMapping("/posts")
    fun recent(@RequestParam(defaultValue = "0") page: Int): PostPage = published.recent(page)

    /**
     * An old slug answers 301 with the current address in `Location`; the body carries the same
     * address in parts for a frontend that routes by itself (01-rules 글 slug, 옛 slug).
     */
    @GetMapping("/blogs/{slug}/posts/{postSlug}")
    fun post(@PathVariable slug: String, @PathVariable postSlug: String): ResponseEntity<Any> =
        when (val lookup = published.find(slug, postSlug)) {
            null -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("POST_NOT_FOUND", "No such post."))
            is PublishedPostLookup.Found -> ResponseEntity.ok(lookup.post)
            is PublishedPostLookup.Moved -> ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, currentAddress(lookup))
                .body(lookup)
        }

    private companion object {
        const val MAX_IDS = 100
    }

    private fun currentAddress(moved: PublishedPostLookup.Moved): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/blogs/{blog}/posts/{post}")
            .buildAndExpand(moved.blogSlug, moved.currentSlug)
            .encode()
            .toUriString()
}
