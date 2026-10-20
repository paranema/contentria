package com.contentria.publishing.adapter.required.member

import com.contentria.member.api.AccountStatusView
import com.contentria.member.api.AccountStatusView.Status
import com.contentria.member.api.GetAccountStatusQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class MemberBloggerStatusAdapterTest {

    private val statuses = mutableMapOf<UUID, Status>()

    private val adapter = MemberBloggerStatusAdapter(object : GetAccountStatusQuery {
        override fun get(accountId: UUID) = statuses[accountId]?.let { AccountStatusView(accountId, it) }
        override fun getAll(accountIds: Set<UUID>) = accountIds.mapNotNull(::get).associateBy { it.accountId }
    })

    @Test
    @DisplayName("활성 계정만 글을 쓸 수 있다")
    fun `only an active account may write`() {
        val active = UUID.randomUUID().also { statuses[it] = Status.ACTIVE }
        val suspended = UUID.randomUUID().also { statuses[it] = Status.SUSPENDED }
        val closed = UUID.randomUUID().also { statuses[it] = Status.CLOSED }

        assertThat(adapter.canPublish(active)).isTrue()
        assertThat(adapter.canPublish(suspended)).isFalse()
        assertThat(adapter.canPublish(closed)).isFalse()
    }

    @Test
    @DisplayName("회원이 모르는 계정은 쓸 수 없다")
    fun `an unknown account may not write`() {
        assertThat(adapter.canPublish(UUID.randomUUID())).isFalse()
    }
}
