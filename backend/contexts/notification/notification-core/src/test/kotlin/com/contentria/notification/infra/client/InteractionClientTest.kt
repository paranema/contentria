package com.contentria.notification.infra.client

import com.contentria.interaction.api.GetSubscribersQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID

class InteractionClientTest {

    /** Pages the way interaction does: in order, after the given id, PAGE_SIZE at most. */
    private class Pages(count: Int) : GetSubscribersQuery {
        val subscribers = List(count) { UUID.randomUUID() }.sortedBy { it.toString() }
        var calls = 0

        override fun subscriberIds(blogId: UUID, after: UUID?): List<UUID> {
            calls++
            return subscribers.filter { after == null || it.toString() > after.toString() }.take(GetSubscribersQuery.PAGE_SIZE)
        }
    }

    @ParameterizedTest(name = "구독자 {0}명은 {1}번에 나눠 받는다")
    @CsvSource("0, 1", "1, 1", "999, 1", "1000, 2", "1001, 2", "2500, 3")
    @DisplayName("구독자를 페이지마다 이어 받아 빠짐없이, 겹치지 않게 모은다")
    fun `gathers every page`(count: Int, calls: Int) {
        val pages = Pages(count)

        val subscribers = InteractionClient(pages).subscribersOf(UUID.randomUUID())

        assertThat(subscribers).containsExactlyElementsOf(pages.subscribers)
        assertThat(pages.calls).isEqualTo(calls)
    }
}
