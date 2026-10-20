package com.contentria.platform.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxRelayTest {

    private val postgres = PostgreSQLContainer("postgres:17")
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private lateinit var jdbc: JdbcTemplate
    private lateinit var transactions: TransactionTemplate
    private lateinit var writer: OutboxWriter

    private data class Sent(val topic: String, val key: String, val envelope: EventEnvelope)

    private val sent = mutableListOf<Sent>()

    /** Fails on the message whose key is this aggregate id. */
    private var failOn: UUID? = null

    private val sender = EventSender { topic, key, value ->
        check(key != failOn?.toString()) { "broker down" }
        sent += Sent(topic, key, EventEnvelope.parse(value))
    }

    private data class Probe(val id: UUID, val note: String)

    @BeforeAll
    fun start() {
        postgres.start()
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        jdbc = JdbcTemplate(dataSource)
        transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))
        jdbc.execute(
            """
            CREATE SCHEMA probe;
            CREATE TABLE probe.outbox_event (
                id uuid PRIMARY KEY, aggregate_type varchar(50) NOT NULL, aggregate_id uuid NOT NULL,
                event_type varchar(100) NOT NULL, event_version int NOT NULL, payload jsonb NOT NULL,
                occurred_at timestamp(6) with time zone NOT NULL, published_at timestamp(6) with time zone
            )
            """.trimIndent(),
        )
        writer = OutboxWriter(jdbc, "probe")
    }

    @AfterAll
    fun stop() = postgres.stop()

    @BeforeEach
    fun reset() {
        jdbc.update("DELETE FROM probe.outbox_event")
        sent.clear()
        failOn = null
    }

    private fun relay() = OutboxRelay(jdbc, transactions, sender, listOf(OutboxSource("probe")), Clock.fixed(now, ZoneOffset.UTC))

    private fun record(aggregate: UUID, at: Instant, note: String = "n"): UUID =
        transactions.execute { writer.record("thing", aggregate, "ThingHappened", 2, Probe(aggregate, note), at) }!!

    @Test
    @DisplayName("보내지 않은 이벤트를 발생 순서대로 <스키마>.<애그리거트> 토픽에 애그리거트 ID를 키로 보내고, 보낸 시각을 남긴다")
    fun `sends unsent events in order`() {
        val aggregate = UUID.randomUUID()
        val later = record(aggregate, Instant.parse("2026-10-20T00:00:02Z"), "later")
        val earlier = record(aggregate, Instant.parse("2026-10-20T00:00:01Z"), "earlier")

        assertThat(relay().relayOnce()).isEqualTo(2)

        assertThat(sent.map { it.envelope.eventId }).containsExactly(earlier, later)
        val first = sent.first()
        assertThat(first.topic).isEqualTo("probe.thing")
        assertThat(first.key).isEqualTo(aggregate.toString())
        assertThat(first.envelope.type).isEqualTo("ThingHappened")
        assertThat(first.envelope.version).isEqualTo(2)
        assertThat(first.envelope.occurredAt).isEqualTo(Instant.parse("2026-10-20T00:00:01Z"))
        assertThat(first.envelope.payloadAs<Probe>()).isEqualTo(Probe(aggregate, "earlier"))
        assertThat(jdbc.queryForList("SELECT published_at FROM probe.outbox_event", java.sql.Timestamp::class.java).map { it!!.toInstant() })
            .containsOnly(now)

        assertThat(relay().relayOnce()).isEqualTo(0)
        assertThat(sent).hasSize(2)
    }

    @Test
    @DisplayName("보내다 실패하면 그때까지 보낸 것만 표시하고, 나머지는 다음 차례에 같은 순서로 보낸다")
    fun `a failure keeps what went out`() {
        val first = record(UUID.randomUUID(), Instant.parse("2026-10-20T00:00:01Z"))
        val stuck = UUID.randomUUID()
        val second = record(stuck, Instant.parse("2026-10-20T00:00:02Z"))
        val third = record(UUID.randomUUID(), Instant.parse("2026-10-20T00:00:03Z"))
        failOn = stuck

        assertThat(relay().relayOnce()).isEqualTo(1)
        assertThat(unsent()).containsExactlyInAnyOrder(second, third)

        failOn = null
        assertThat(relay().relayOnce()).isEqualTo(2)
        assertThat(sent.map { it.envelope.eventId }).containsExactly(first, second, third)
    }

    @Test
    @DisplayName("다른 릴레이가 같은 아웃박스를 비우는 중이면 이번 차례를 건너뛴다")
    fun `one relay at a time`() {
        record(UUID.randomUUID(), Instant.parse("2026-10-20T00:00:01Z"))

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { other ->
            other.autoCommit = false
            other.prepareStatement("SELECT pg_advisory_xact_lock(hashtext('outbox-relay:probe'))").execute()

            assertThat(relay().relayOnce()).isEqualTo(0)
            other.rollback()
        }
        assertThat(relay().relayOnce()).isEqualTo(1)
    }

    @Test
    @DisplayName("보낸 지 오래된 이벤트만 지우고, 보내지 않은 이벤트는 오래되어도 남긴다")
    fun `purge keeps unsent events`() {
        val old = record(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"))
        relay().relayOnce()
        val unsentOld = record(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"))

        assertThat(relay().purge(now.plusSeconds(1))).isEqualTo(1)
        assertThat(jdbc.queryForList("SELECT id FROM probe.outbox_event", UUID::class.java)).containsExactly(unsentOld).doesNotContain(old)
    }

    private fun unsent(): List<UUID> = jdbc.queryForList("SELECT id FROM probe.outbox_event WHERE published_at IS NULL", UUID::class.java).filterNotNull()
}
