package com.contentria.interaction.presentation.web

import com.contentria.interaction.application.LikeService
import com.contentria.interaction.application.SubscribedBlog
import com.contentria.interaction.application.SubscriptionService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * docs/contexts/interaction/01-rules.md, 흐름 1과 2. PUT and DELETE describe the state to reach,
 * so sending either twice gives the same answer.
 */
@RestController
class SubscriptionLikeController(
    private val subscriptions: SubscriptionService,
    private val likes: LikeService,
) {

    @PutMapping("/blogs/{slug}/subscription")
    fun subscribe(@AuthenticationPrincipal accountId: UUID, @PathVariable slug: String): ResponseEntity<Void> {
        subscriptions.subscribe(accountId, slug)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/blogs/{slug}/subscription")
    fun unsubscribe(@AuthenticationPrincipal accountId: UUID, @PathVariable slug: String): ResponseEntity<Void> {
        subscriptions.unsubscribe(accountId, slug)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me/subscriptions")
    fun mySubscriptions(@AuthenticationPrincipal accountId: UUID): List<SubscribedBlog> = subscriptions.subscribedBlogs(accountId)

    @GetMapping("/me/subscriptions/{slug}")
    fun isSubscribed(@AuthenticationPrincipal accountId: UUID, @PathVariable slug: String): SubscriptionStatus =
        SubscriptionStatus(subscriptions.isSubscribed(accountId, slug))

    @PutMapping("/posts/{id}/like")
    fun like(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID): ResponseEntity<Void> {
        likes.like(accountId, id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/posts/{id}/like")
    fun unlike(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID): ResponseEntity<Void> {
        likes.unlike(accountId, id)
        return ResponseEntity.noContent().build()
    }

    /** Which of these posts the caller has liked. At most [MAX_IDS] ids. */
    @GetMapping("/me/likes")
    fun myLikes(@AuthenticationPrincipal accountId: UUID, @RequestParam postIds: Set<UUID>): LikedPosts =
        LikedPosts(likes.likedBy(accountId, postIds.limited()))
}

data class SubscriptionStatus(val subscribed: Boolean)

data class LikedPosts(val postIds: Set<UUID>)
