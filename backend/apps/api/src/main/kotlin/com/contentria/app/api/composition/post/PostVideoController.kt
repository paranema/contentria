package com.contentria.app.api.composition.post

import com.contentria.media.api.GetPostVideosQuery
import com.contentria.media.api.PostVideo
import com.contentria.publishing.api.GetPostReferencesQuery
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * A post's video for its page (docs/contexts/media/01-rules.md, 흐름 V4), put together from
 * publishing, which says whether readers may see the post, and media, which says what to play.
 */
@RestController
class PostVideoController(
    private val posts: GetPostReferencesQuery,
    private val videos: GetPostVideosQuery,
) {

    @GetMapping("/posts/{id}/video")
    fun video(@PathVariable id: UUID): ResponseEntity<Any> {
        // A draft's or private post's video answers as no video, like the post itself.
        if (posts.getPosts(setOf(id))[id]?.isPublic != true) return notFound()
        val video: PostVideo = videos.videosOf(setOf(id))[id] ?: return notFound()
        return ResponseEntity.ok(video)
    }

    private fun notFound(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("code" to "VIDEO_NOT_FOUND", "message" to "The post has no video to show."))
}
