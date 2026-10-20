package com.contentria.platform.messaging

import com.contentria.shared.id.Ids
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Records an event in a context's own `<schema>.outbox_event`, inside the transaction that is
 * already running (docs/architecture/03-context-integration.md, 아웃박스). Never sends to Kafka:
 * the relay does that after commit, so a rolled-back change leaves no event behind.
 *
 * One writer per context, each bound to its schema. There is no shared outbox table; a context
 * that moves to its own service takes its schema, outbox included.
 */
class OutboxWriter(
    private val jdbc: JdbcTemplate,
    schema: String,
) {

    private val insert = "INSERT INTO $schema.outbox_event " +
        "(id, aggregate_type, aggregate_id, event_type, event_version, payload, occurred_at) " +
        "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)"

    init {
        require(SCHEMA.matches(schema)) { "Not a schema name: $schema" }
    }

    /**
     * [aggregateType] picks the topic (`<schema>.<aggregateType>`); [aggregateId] is the message
     * key, so one aggregate's events stay in order. Returns the event id.
     */
    fun record(aggregateType: String, aggregateId: UUID, eventType: String, eventVersion: Int, payload: Any, occurredAt: Instant): UUID {
        // Outside a transaction the event would commit on its own, whatever happens to the change it reports.
        check(TransactionSynchronizationManager.isActualTransactionActive()) { "An outbox event is recorded inside the transaction that makes the change" }
        val eventId = Ids.newId()
        jdbc.update(insert, eventId, aggregateType, aggregateId, eventType, eventVersion, json.writeValueAsString(payload), Timestamp.from(occurredAt))
        return eventId
    }

    private companion object {
        val SCHEMA = Regex("^[a-z][a-z_]*$")

        /** Instants as ISO-8601 text: Jackson 3 writes dates as text by default. */
        val json: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    }
}
