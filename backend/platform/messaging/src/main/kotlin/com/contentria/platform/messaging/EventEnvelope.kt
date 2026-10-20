package com.contentria.platform.messaging

import tools.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

/**
 * What travels on a topic (docs/architecture/03-context-integration.md, 메시지 형식). The payload
 * is the producing context's event class from its `-api`, kept as JSON until a consumer that
 * knows the [type] and [version] reads it.
 */
data class EventEnvelope(
    val eventId: UUID,
    val type: String,
    val version: Int,
    val occurredAt: Instant,
    val aggregateId: UUID,
    val payload: JsonNode,
) {

    inline fun <reified T : Any> payloadAs(): T = payloadAs(T::class.java)

    fun <T : Any> payloadAs(type: Class<T>): T = MessagingJson.mapper.treeToValue(payload, type)

    fun toJson(): String = MessagingJson.mapper.writeValueAsString(this)

    companion object {
        /** Throws Jackson's exception for anything that is not an envelope; the listener sends it straight to the dead letters. */
        fun parse(json: String): EventEnvelope = MessagingJson.mapper.readValue(json, EventEnvelope::class.java)
    }
}
