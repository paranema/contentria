package com.contentria.notification.presentation.scheduling

import com.contentria.notification.application.NotificationRetention
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Once a day, at a quiet hour. Every replica runs it; a second run finds nothing left to remove. */
@Component
class NotificationRetentionSchedule(private val retention: NotificationRetention) {

    @Scheduled(cron = "0 41 4 * * *", zone = "Asia/Seoul")
    fun removeExpired() {
        retention.removeExpired()
    }
}
