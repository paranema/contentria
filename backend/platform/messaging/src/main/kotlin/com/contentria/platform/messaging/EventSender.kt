package com.contentria.platform.messaging

import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Hands one message to the broker and returns once the broker has it, or throws. */
fun interface EventSender {
    fun send(topic: String, key: String, value: String)
}

class KafkaEventSender(
    private val kafka: KafkaTemplate<String, String>,
    private val timeout: Duration = Duration.ofSeconds(10),
) : EventSender {

    override fun send(topic: String, key: String, value: String) {
        kafka.send(topic, key, value).get(timeout.toMillis(), TimeUnit.MILLISECONDS)
    }
}
