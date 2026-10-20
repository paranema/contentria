package com.contentria.member.api

import java.util.UUID

/**
 * Whether accounts may act, for contexts whose own rules depend on it: publishing refuses writes
 * from a blogger who is not active (docs/strategic/04-context-map.md).
 *
 * Asked by id batch, as if it were a remote call (docs/architecture/03-context-integration.md,
 * 조회는 원격 호출처럼 다룬다). An id this context does not know is missing from the result.
 */
interface GetAccountStatusQuery {

    fun get(accountId: UUID): AccountStatusView?

    fun getAll(accountIds: Set<UUID>): Map<UUID, AccountStatusView>
}

data class AccountStatusView(val accountId: UUID, val status: Status) {

    val isActive: Boolean get() = status == Status.ACTIVE

    /** member's account states as other contexts see them. Not the domain's own enum. */
    enum class Status {
        ACTIVE,
        SUSPENDED,

        /** Withdrawn by the owner. */
        CLOSED,
    }
}
