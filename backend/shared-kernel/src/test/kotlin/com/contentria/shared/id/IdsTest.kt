package com.contentria.shared.id

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier

class IdsTest {

    @Test
    @DisplayName("RFC 9562의 UUID 버전 7, variant 2를 발급한다")
    fun `issues version 7 identifiers`() {
        val id = Ids.newId()

        assertThat(id.version()).isEqualTo(7)
        assertThat(id.variant()).isEqualTo(2)
    }

    @Test
    @DisplayName("연속 발급한 식별자는 서로 다르다")
    fun `successive identifiers are distinct`() {
        val ids = List(10_000) { Ids.newId() }

        assertThat(ids.toSet()).hasSize(ids.size)
    }

    @Test
    @DisplayName("선행 비트가 실제 발급 시각을 담는다")
    fun `the leading bits carry the wall clock time of issue`() {
        val before = System.currentTimeMillis()
        val id = Ids.newId()
        val after = System.currentTimeMillis()

        assertThat(millisOf(id)).isBetween(before, after)
    }

    /**
     * The reason for choosing UUIDv7 at all. Postgres compares `uuid` values as unsigned bytes,
     * so an index on the primary key stores rows in this order. Identifiers that increase in it
     * append to the right edge of the B-tree; identifiers that do not scatter writes across it.
     *
     * The assertion is strict rather than merely sorted, because a burst of inserts lands inside
     * one millisecond and that is exactly where a non-monotonic generator gives up ordering.
     */
    @Test
    @DisplayName("데이터베이스 인덱스가 보는 순서로 엄격히 증가한다. 같은 밀리초 안에서도 뒤집히지 않는다")
    fun `identifiers strictly increase in index order`() {
        val ids = List(10_000) { Ids.newId() }

        val outOfOrder = ids.zipWithNext().filter { (earlier, later) -> INDEX_ORDER.compare(earlier, later) >= 0 }

        assertThat(outOfOrder).isEmpty()
    }

    @Test
    @DisplayName("여러 스레드가 동시에 발급해도 충돌하지 않는다")
    fun `concurrent issuers never collide`() {
        val threadCount = 8
        val perThread = 2_000
        val issued = ConcurrentLinkedQueue<UUID>()
        val startTogether = CyclicBarrier(threadCount)

        val threads = List(threadCount) {
            Thread {
                startTogether.await()
                repeat(perThread) { issued.add(Ids.newId()) }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertThat(issued).hasSize(threadCount * perThread)
        assertThat(issued.toSet()).hasSize(threadCount * perThread)
    }

    private companion object {

        /** Unsigned, high half first. The order a Postgres `uuid` column is indexed in. */
        val INDEX_ORDER = Comparator<UUID> { left, right ->
            val high = left.mostSignificantBits.toULong().compareTo(right.mostSignificantBits.toULong())
            if (high != 0) high
            else left.leastSignificantBits.toULong().compareTo(right.leastSignificantBits.toULong())
        }
    }

    /** UUIDv7 lays the 48-bit Unix millisecond timestamp in the high bits of the high half. */
    private fun millisOf(id: UUID): Long = id.mostSignificantBits ushr 16
}
