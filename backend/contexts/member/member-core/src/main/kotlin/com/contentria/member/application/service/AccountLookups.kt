package com.contentria.member.application.service

import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.domain.account.Account
import com.contentria.member.domain.account.AccountException
import java.util.UUID

/**
 * The signed-in account, for a use case that changes it. An access token can outlive a suspension
 * by up to its lifetime, so the status is checked here rather than trusted from the token.
 */
internal fun AccountRepository.loadActive(accountId: UUID): Account {
    val account = findById(accountId) ?: throw MemberUseCaseException.InvalidSession()
    if (!account.status.canAuthenticate) throw AccountException.NotAuthenticatable(account.status)
    return account
}
