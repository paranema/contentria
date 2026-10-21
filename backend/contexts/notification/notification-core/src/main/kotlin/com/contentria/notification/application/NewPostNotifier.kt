package com.contentria.notification.application

import com.contentria.notification.domain.notification.Notification
import com.contentria.notification.infra.client.InteractionClient
import com.contentria.notification.infra.persistence.NotificationWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * docs/contexts/notification/01-rules.md, 흐름 1. Two steps so that interaction is asked before the
 * write transaction opens: [subscribersOf] outside it, [notify] inside the inbox's transaction.
 */
@Service
class NewPostNotifier(
    private val interaction: InteractionClient,
    private val writer: NotificationWriter,
    private val clock: Clock,
) {

    /** The blog's subscribers as of now. Asks interaction; call it outside any transaction. */
    fun subscribersOf(blogId: UUID): List<UUID> = interaction.subscribersOf(blogId)

    /** One new-post notification for each of [subscribers]; none when there are none (흐름 1의 1번). */
    @Transactional
    fun notify(eventId: UUID, postId: UUID, blogId: UUID, subscribers: List<UUID>) {
        val at = clock.instant()
        writer.insertAll(subscribers.map { Notification.newPost(it, postId, blogId, eventId, at) })
    }
}
