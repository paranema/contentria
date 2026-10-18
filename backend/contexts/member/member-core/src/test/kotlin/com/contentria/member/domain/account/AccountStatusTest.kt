package com.contentria.member.domain.account

import com.contentria.member.domain.account.AccountStatus.ACTIVE
import com.contentria.member.domain.account.AccountStatus.DELETED
import com.contentria.member.domain.account.AccountStatus.SUSPENDED
import com.contentria.member.domain.account.AccountStatus.UNVERIFIED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AccountStatus 전이 규칙")
class AccountStatusTest {

    @Test
    @DisplayName("세션을 시작할 수 있는 상태는 ACTIVE 하나뿐이다")
    fun `only ACTIVE can authenticate`() {
        assertThat(ACTIVE.canAuthenticate).isTrue()
        assertThat(UNVERIFIED.canAuthenticate).isFalse()
        assertThat(SUSPENDED.canAuthenticate).isFalse()
        assertThat(DELETED.canAuthenticate).isFalse()
    }

    @Test
    @DisplayName("DELETED는 종착 상태여서 어디로도 전이하지 않는다")
    fun `DELETED is terminal`() {
        assertThat(AccountStatus.entries.map { DELETED.canTransitionTo(it) })
            .containsOnly(false)
    }

    @Test
    @DisplayName("UNVERIFIED로는 되돌아가지 않는다. 이메일 인증은 가입 시점의 1회성 단계다")
    fun `never returns to UNVERIFIED`() {
        assertThat(AccountStatus.entries.map { it.canTransitionTo(UNVERIFIED) })
            .containsOnly(false)
    }

    @Test
    @DisplayName("같은 상태로의 전이는 변화가 없으므로 허용하지 않는다")
    fun `self transition is rejected`() {
        assertThat(AccountStatus.entries.map { it.canTransitionTo(it) })
            .containsOnly(false)
    }

    @Test
    @DisplayName("정지와 복구는 양방향으로 가능하다")
    fun `suspension is reversible`() {
        assertThat(ACTIVE.canTransitionTo(SUSPENDED)).isTrue()
        assertThat(SUSPENDED.canTransitionTo(ACTIVE)).isTrue()
    }

    @Test
    @DisplayName("미인증 계정도 정지하거나 삭제할 수 있다")
    fun `unverified can be suspended or deleted`() {
        assertThat(UNVERIFIED.canTransitionTo(ACTIVE)).isTrue()
        assertThat(UNVERIFIED.canTransitionTo(SUSPENDED)).isTrue()
        assertThat(UNVERIFIED.canTransitionTo(DELETED)).isTrue()
    }
}
