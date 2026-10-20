package com.contentria.platform.messaging

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

/**
 * A consuming context's `<schema>.inbox_event` (docs/architecture/03-context-integration.md, 소비자와
 * 인박스). The event is recorded in the transaction that handles it, so a repeat finds it recorded
 * and a failed attempt leaves nothing behind for the retry to trip over.
 */
class Inbox(
    private val jdbc: JdbcTemplate,
    private val transactions: TransactionTemplate,
    schema: String,
    private val clock: Clock,
) {

    private val insert = "INSERT INTO ${OutboxSource(schema).schema}.inbox_event (event_id, consumer, processed_at) " +
        "VALUES (?, ?, ?) ON CONFLICT DO NOTHING"

    /**
     * Runs [handle] unless [consumer] has handled [eventId] already, in one transaction with the
     * record. Returns whether it ran. [handle]'s repositories join the transaction.
     */
    fun processOnce(eventId: UUID, consumer: String, handle: () -> Unit): Boolean = transactions.execute {
        val first = jdbc.update(insert, eventId, consumer, Timestamp.from(clock.instant())) == 1
        if (first) handle()
        first
    }!!
}
