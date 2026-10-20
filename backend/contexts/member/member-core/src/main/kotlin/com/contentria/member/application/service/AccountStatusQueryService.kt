package com.contentria.member.application.service

import com.contentria.member.api.AccountStatusView
import com.contentria.member.api.GetAccountStatusQuery
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.domain.account.AccountStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Account status for other contexts. Runs in its own read-only transaction rather than the
 * caller's, which is how it will behave once it sits behind a network call.
 */
@Service
class AccountStatusQueryService(private val accounts: AccountRepository) : GetAccountStatusQuery {

    @Transactional(readOnly = true)
    override fun get(accountId: UUID): AccountStatusView? =
        accounts.findById(accountId)?.let { AccountStatusView(it.id, it.status.published()) }

    @Transactional(readOnly = true)
    override fun getAll(accountIds: Set<UUID>): Map<UUID, AccountStatusView> {
        if (accountIds.isEmpty()) return emptyMap()
        return accounts.findByIdIn(accountIds).associate { it.id to AccountStatusView(it.id, it.status.published()) }
    }

    private fun AccountStatus.published(): AccountStatusView.Status = when (this) {
        AccountStatus.ACTIVE -> AccountStatusView.Status.ACTIVE
        AccountStatus.SUSPENDED -> AccountStatusView.Status.SUSPENDED
        AccountStatus.DELETED -> AccountStatusView.Status.CLOSED
    }
}
