package com.contentria.app.api

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.UUID

/** Reading and writing the test Kafka directly, and waiting for what consumers do. */
object TestKafka {

    val TIMEOUT: Duration = Duration.ofSeconds(30)

    /** Waits until [condition] holds, polling the way a reader would. */
    fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TIMEOUT.toNanos()
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Not within $TIMEOUT" }
            Thread.sleep(100)
        }
    }

    /** The value of the first message on [topic], read from its start, that [matches]. */
    fun read(topic: String, matches: (ConsumerRecord<String, String>) -> Boolean): String {
        val config = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to ApiTestEnvironment.kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        )
        KafkaConsumer(config, StringDeserializer(), StringDeserializer()).use { consumer ->
            consumer.subscribe(listOf(topic))
            val deadline = System.nanoTime() + TIMEOUT.toNanos()
            while (System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).firstOrNull(matches)?.let { return it.value() }
            }
        }
        error("No such message on $topic within $TIMEOUT")
    }

    fun produce(topic: String, key: String, value: String) {
        val config = mapOf<String, Any>(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to ApiTestEnvironment.kafka.bootstrapServers)
        KafkaProducer(config, StringSerializer(), StringSerializer()).use { it.send(ProducerRecord(topic, key, value)).get() }
    }
}
