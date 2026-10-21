package com.contentria.notification.domain.notification

import org.springframework.data.repository.Repository
import java.time.Instant
import java.util.UUID

// Spring Data marker repository, in the domain as the layered structure has it
// (docs/architecture/02-internal-architecture.md, 리포지토리). Only the methods declared here exist.
// New notifications, marking all read and expiry are batch statements in infra/persistence.
interface NotificationRepository : Repository<Notification, UUID> {
    fun findById(id: UUID): Notification?
    fun countByRecipientIdAndReadAtIsNull(recipientId: UUID): Long

    /** [Notification]'s named query of the same name. */
    fun findPageFor(recipientId: UUID, beforeAt: Instant, beforeId: UUID): List<Notification>
}
