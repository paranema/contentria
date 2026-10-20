package com.contentria.platform.messaging

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxWriterTest {

    private val postgres = PostgreSQLContainer("postgres:17")
    private lateinit var jdbc: JdbcTemplate
    private lateinit var transactions: TransactionTemplate
    private lateinit var writer: OutboxWriter

    private data class Probe(val id: UUID, val at: Instant)

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

    @Test
    @DisplayName("커밋하면 이벤트가 남고, 본문은 JSON이고, 아직 보내지 않은 상태다")
    fun `a committed event stays`() {
        val aggregate = UUID.randomUUID()
        val at = Instant.parse("2026-10-20T00:00:00Z")

        val eventId = transactions.execute { writer.record("thing", aggregate, "ThingHappened", 1, Probe(aggregate, at), at) }!!

        val row = jdbc.queryForMap("SELECT * FROM probe.outbox_event WHERE id = ?", eventId)
        assertThat(row["aggregate_type"]).isEqualTo("thing")
        assertThat(row["aggregate_id"]).isEqualTo(aggregate)
        assertThat(row["event_type"]).isEqualTo("ThingHappened")
        assertThat(row["published_at"]).isNull()
        assertThat(row["payload"].toString()).contains("\"id\": \"$aggregate\"", "\"at\": \"2026-10-20T00:00:00Z\"")
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 이벤트도 남지 않는다")
    fun `a rolled back event is gone`() {
        val aggregate = UUID.randomUUID()

        assertThatThrownBy {
            transactions.execute {
                writer.record("thing", aggregate, "ThingHappened", 1, Probe(aggregate, Instant.now()), Instant.now())
                error("the change that went with the event failed")
            }
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(jdbc.queryForObject("SELECT count(*) FROM probe.outbox_event WHERE aggregate_id = ?", Long::class.java, aggregate)).isZero()
    }

    @Test
    @DisplayName("트랜잭션 밖에서는 기록하지 않는다. 바꾼 내용과 따로 커밋되기 때문이다")
    fun `refuses to record outside a transaction`() {
        assertThatThrownBy { writer.record("thing", UUID.randomUUID(), "ThingHappened", 1, "x", Instant.now()) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    @DisplayName("스키마 이름이 아닌 값은 받지 않는다. SQL에 그대로 들어가기 때문이다")
    fun `schema must be a name`() {
        assertThatThrownBy { OutboxWriter(jdbc, "probe; drop table x") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
