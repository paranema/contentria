package com.contentria.platform.messaging

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.core.JacksonException
import java.util.UUID

class EventEnvelopeTest {

    private data class Probe(val id: UUID)

    @Test
    @DisplayName("소비자는 모르는 필드를 무시한다. 생산자는 필드를 더해도 버전을 올리지 않는다")
    fun `unknown fields are ignored`() {
        val id = UUID.randomUUID()
        val json = """
            {"eventId":"$id","type":"ProbeHappened","version":1,"occurredAt":"2026-10-20T00:00:00Z",
             "aggregateId":"$id","payload":{"id":"$id","addedLater":true},"traceId":"abc"}
        """.trimIndent()

        val envelope = EventEnvelope.parse(json)

        assertThat(envelope.type).isEqualTo("ProbeHappened")
        assertThat(envelope.payloadAs<Probe>()).isEqualTo(Probe(id))
        assertThat(EventEnvelope.parse(envelope.toJson())).isEqualTo(envelope)
    }

    @Test
    @DisplayName("봉투가 아닌 메시지는 Jackson 예외로 거절한다. 다시 시도하지 않고 DLT로 보내는 기준이다")
    fun `not an envelope`() {
        assertThatThrownBy { EventEnvelope.parse("not json") }.isInstanceOf(JacksonException::class.java)
        assertThatThrownBy { EventEnvelope.parse("""{"type":"x"}""") }.isInstanceOf(JacksonException::class.java)
    }
}
