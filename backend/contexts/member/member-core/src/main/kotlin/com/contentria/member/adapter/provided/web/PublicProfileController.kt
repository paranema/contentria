package com.contentria.member.adapter.provided.web

import com.contentria.member.application.provided.GetPublicProfilesQuery
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 흐름 5, 공개 범위: anyone's profile, signed in or not. A screen asks for every author on it at
 * once (`?ids=a,b,c`) rather than one call per post or comment.
 */
@RestController
@RequestMapping("/members/profiles")
class PublicProfileController(private val profiles: GetPublicProfilesQuery) {

    @GetMapping
    fun get(@RequestParam ids: Set<UUID>): ResponseEntity<Any> {
        if (ids.size > MAX_IDS) {
            return ResponseEntity.badRequest().body(ErrorResponse("INVALID_REQUEST", "Ask for at most $MAX_IDS members at once."))
        }
        return ResponseEntity.ok(profiles.get(ids))
    }

    private companion object {
        /** More than a page of posts or comments ever shows. */
        const val MAX_IDS = 100
    }
}
