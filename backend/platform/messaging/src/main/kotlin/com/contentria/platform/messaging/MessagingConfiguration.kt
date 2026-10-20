package com.contentria.platform.messaging

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.util.backoff.ExponentialBackOff
import tools.jackson.core.JacksonException
import java.time.Clock

/**
 * The relay for every [OutboxSource] in the process, and how listeners fail: retries at growing
 * intervals, then the dead letter topic `<topic>-dlt`, so one message never blocks its partition
 * (03-context-integration, 소비자와 인박스). Loaded by the app that hosts the contexts; Boot's
 * Kafka auto-configuration provides the template and picks up the error handler.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class MessagingConfiguration {

    @Bean
    fun eventSender(kafka: KafkaTemplate<String, String>): EventSender = KafkaEventSender(kafka)

    @Bean
    fun outboxRelay(
        jdbc: JdbcTemplate,
        transactions: TransactionTemplate,
        sender: EventSender,
        sources: ObjectProvider<OutboxSource>,
        clock: Clock,
    ) = OutboxRelay(jdbc, transactions, sender, sources.orderedStream().toList(), clock)

    @Bean
    fun outboxRelayScheduler(relay: OutboxRelay, clock: Clock) = OutboxRelayScheduler(relay, clock)

    @Bean
    fun listenerErrorHandler(kafka: KafkaTemplate<String, String>): CommonErrorHandler {
        val backOff = ExponentialBackOff(1_000, 2.0).apply { maxElapsedTime = 60_000 }
        return DefaultErrorHandler(DeadLetterPublishingRecoverer(kafka), backOff).apply {
            // Retrying cannot fix a message that is not an envelope.
            addNotRetryableExceptions(JacksonException::class.java)
        }
    }
}
