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
import java.time.Clock
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InboxTest {

    private val postgres = PostgreSQLContainer("postgres:17")
    private lateinit var jdbc: JdbcTemplate
    private lateinit var inbox: Inbox

    @BeforeAll
    fun start() {
        postgres.start()
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        jdbc = JdbcTemplate(dataSource)
        jdbc.execute(
            """
            CREATE SCHEMA probe;
            CREATE TABLE probe.inbox_event (
                event_id uuid NOT NULL, consumer varchar(100) NOT NULL,
                processed_at timestamp(6) with time zone NOT NULL, PRIMARY KEY (event_id, consumer)
            );
            CREATE TABLE probe.handled (event_id uuid NOT NULL);
            """.trimIndent(),
        )
        inbox = Inbox(jdbc, TransactionTemplate(DataSourceTransactionManager(dataSource)), "probe", Clock.systemUTC())
    }

    @AfterAll
    fun stop() = postgres.stop()

    private fun handle(eventId: UUID) {
        jdbc.update("INSERT INTO probe.handled VALUES (?)", eventId)
    }

    private fun handled(eventId: UUID): Int =
        jdbc.queryForObject("SELECT count(*) FROM probe.handled WHERE event_id = ?", Int::class.java, eventId)!!

    @Test
    @DisplayName("같은 소비자에게 같은 이벤트가 다시 오면 처리하지 않는다. 다른 소비자는 따로 처리한다")
    fun `once per consumer`() {
        val eventId = UUID.randomUUID()

        assertThat(inbox.processOnce(eventId, "probe.first") { handle(eventId) }).isTrue()
        assertThat(inbox.processOnce(eventId, "probe.first") { handle(eventId) }).isFalse()
        assertThat(inbox.processOnce(eventId, "probe.second") { handle(eventId) }).isTrue()

        assertThat(handled(eventId)).isEqualTo(2)
    }

    @Test
    @DisplayName("처리가 실패하면 기록도 남지 않아, 다시 시도하면 처리한다")
    fun `a failure leaves nothing behind`() {
        val eventId = UUID.randomUUID()

        assertThatThrownBy {
            inbox.processOnce(eventId, "probe.first") {
                handle(eventId)
                error("handler failed")
            }
        }.hasMessage("handler failed")
        assertThat(handled(eventId)).isZero()

        assertThat(inbox.processOnce(eventId, "probe.first") { handle(eventId) }).isTrue()
        assertThat(handled(eventId)).isEqualTo(1)
    }
}
