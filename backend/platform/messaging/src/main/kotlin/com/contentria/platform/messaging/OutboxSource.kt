package com.contentria.platform.messaging

/**
 * A context's outbox, `<schema>.outbox_event`, for the relay to drain. Each context that records
 * events declares one as a bean in its core configuration.
 */
data class OutboxSource(val schema: String) {
    init {
        require(Regex("^[a-z][a-z_]*$").matches(schema)) { "Not a schema name: $schema" }
    }
}
