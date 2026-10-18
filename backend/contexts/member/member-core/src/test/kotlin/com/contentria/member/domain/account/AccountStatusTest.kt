package com.contentria.member.domain.account

import com.contentria.member.domain.account.AccountStatus.ACTIVE
import com.contentria.member.domain.account.AccountStatus.DELETED
import com.contentria.member.domain.account.AccountStatus.SUSPENDED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AccountStatus 전이 규칙")
class AccountStatusTest {

    @Test
    @DisplayName("미확인 상태는 없다. 계정은 주소 확인을 마친 뒤에만 만들어진다")
    fun `there is no unverified status`() {
        assertThat(AccountStatus.entries).containsExactly(ACTIVE, SUSPENDED, DELETED)
    }

    @Test
    @DisplayName("세션을 시작할 수 있는 상태는 ACTIVE 하나뿐이다")
    fun `only ACTIVE can authenticate`() {
        assertThat(ACTIVE.canAuthenticate).isTrue()
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
    @DisplayName("활성과 정지 상태 모두 삭제로 갈 수 있다. 정지된 회원의 탈퇴를 막는 것은 애플리케이션 정책이다")
    fun `both live states can be deleted`() {
        assertThat(ACTIVE.canTransitionTo(DELETED)).isTrue()
        assertThat(SUSPENDED.canTransitionTo(DELETED)).isTrue()
    }
}
