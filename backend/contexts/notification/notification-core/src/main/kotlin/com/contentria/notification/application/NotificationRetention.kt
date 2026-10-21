package com.contentria.notification.application

import com.contentria.notification.infra.persistence.NotificationWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/** 01-rules, 처음 정하는 값: a notification lives 90 days from when it was made, read or not. */
@Service
class NotificationRetention(
    private val writer: NotificationWriter,
    private val clock: Clock,
) {

    /** Returns how many went. Running it twice, or on two replicas at once, removes nothing more. */
    @Transactional
    fun removeExpired(): Int = writer.deleteMadeBefore(clock.instant().minus(PERIOD))

    companion object {
        val PERIOD: Duration = Duration.ofDays(90)
    }
}
