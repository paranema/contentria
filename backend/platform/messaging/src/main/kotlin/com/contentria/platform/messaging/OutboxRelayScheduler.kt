package com.contentria.platform.messaging

import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Duration

/** Runs the relay in the process that hosts the producing contexts (currently apps/api). */
class OutboxRelayScheduler(private val relay: OutboxRelay, private val clock: Clock) {

    @Scheduled(fixedDelayString = "\${contentria.messaging.relay.interval:1s}")
    fun relay() {
        relay.relayOnce()
    }

    @Scheduled(cron = "0 17 * * * *")
    fun purge() {
        relay.purge(clock.instant().minus(RETENTION))
    }

    private companion object {
        /** Long enough to look back at what went out; the topics keep their own copies. */
        val RETENTION: Duration = Duration.ofDays(7)
    }
}
