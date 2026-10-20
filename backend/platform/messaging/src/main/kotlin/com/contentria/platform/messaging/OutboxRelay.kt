package com.contentria.platform.messaging

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Sends what the contexts recorded in their outboxes (docs/architecture/03-context-integration.md,
 * 릴레이): unsent rows, oldest first, to topic `<schema>.<aggregate type>` keyed by the aggregate id,
 * marking each once the broker has it.
 *
 * One relay at a time per outbox, however many replicas run: each pass holds a PostgreSQL advisory
 * lock for its transaction, and a replica that cannot take it skips the pass. Two relays at once
 * could put one aggregate's events on the topic out of order.
 *
 * Delivery is at least once. A crash between sending and committing the mark sends the event
 * again, and consumers drop the repeat through their inbox.
 */
class OutboxRelay(
    private val jdbc: JdbcTemplate,
    private val transactions: TransactionTemplate,
    private val sender: EventSender,
    private val sources: List<OutboxSource>,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** One pass over every outbox; returns how many events went out. */
    fun relayOnce(): Int = sources.sumOf { relay(it) }

    /** Removes events sent before [sentBefore]. Unsent ones stay, however old. */
    fun purge(sentBefore: Instant): Int = sources.sumOf { source ->
        jdbc.update("DELETE FROM ${source.schema}.outbox_event WHERE published_at < ?", Timestamp.from(sentBefore))
    }

    private fun relay(source: OutboxSource): Int = transactions.execute {
        val mine = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtext(?))", Boolean::class.java, "outbox-relay:${source.schema}")
        if (mine != true) return@execute 0

        var sent = 0
        for (row in jdbc.query(unsent(source), ::toRow)) {
            try {
                sender.send("${source.schema}.${row.aggregateType}", row.envelope.aggregateId.toString(), row.envelope.toJson())
            } catch (e: Exception) {
                // Keep what went out so far and stop: the rest goes next pass, still in order.
                log.warn("Outbox relay stopped at event {} of {}", row.envelope.eventId, source.schema, e)
                break
            }
            jdbc.update("UPDATE ${source.schema}.outbox_event SET published_at = ? WHERE id = ?", Timestamp.from(clock.instant()), row.envelope.eventId)
            sent++
        }
        sent
    }!!

    private fun unsent(source: OutboxSource) =
        "SELECT id, aggregate_type, aggregate_id, event_type, event_version, payload::text AS payload, occurred_at " +
            "FROM ${source.schema}.outbox_event WHERE published_at IS NULL ORDER BY occurred_at, id LIMIT $BATCH_SIZE"

    private fun toRow(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) = Row(
        aggregateType = rs.getString("aggregate_type"),
        envelope = EventEnvelope(
            eventId = rs.getObject("id", UUID::class.java),
            type = rs.getString("event_type"),
            version = rs.getInt("event_version"),
            occurredAt = rs.getTimestamp("occurred_at").toInstant(),
            aggregateId = rs.getObject("aggregate_id", UUID::class.java),
            payload = MessagingJson.mapper.readTree(rs.getString("payload")),
        ),
    )

    private class Row(val aggregateType: String, val envelope: EventEnvelope)

    private companion object {
        const val BATCH_SIZE = 100
    }
}
