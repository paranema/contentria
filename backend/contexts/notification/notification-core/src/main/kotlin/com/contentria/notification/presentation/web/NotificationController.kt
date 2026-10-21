package com.contentria.notification.presentation.web

import com.contentria.notification.application.MyNotificationsService
import com.contentria.notification.application.NotificationPage
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** docs/contexts/notification/01-rules.md, 흐름 3. Signed-in members only, each to their own. */
@RestController
class NotificationController(private val notifications: MyNotificationsService) {

    @GetMapping("/me/notifications")
    fun page(@AuthenticationPrincipal accountId: UUID, @RequestParam(required = false) cursor: String?): NotificationPage =
        notifications.page(accountId, cursor)

    @GetMapping("/me/notifications/unread-count")
    fun unreadCount(@AuthenticationPrincipal accountId: UUID): UnreadCount = UnreadCount(notifications.unreadCount(accountId))

    @PostMapping("/me/notifications/{id}/read")
    fun markRead(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID): ResponseEntity<Void> {
        notifications.markRead(accountId, id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/me/notifications/read-all")
    fun markAllRead(@AuthenticationPrincipal accountId: UUID): ResponseEntity<Void> {
        notifications.markAllRead(accountId)
        return ResponseEntity.noContent().build()
    }
}

data class UnreadCount(val count: Long)
