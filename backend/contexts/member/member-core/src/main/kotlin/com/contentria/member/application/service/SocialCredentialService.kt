package com.contentria.member.application.service

import com.contentria.member.application.provided.GetMyCredentialsQuery
import com.contentria.member.application.provided.IssuedSession
import com.contentria.member.application.provided.LinkSocialProviderUseCase
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.UnlinkSocialProviderUseCase
import com.contentria.member.application.provided.VerifiedSocialIdentity
import com.contentria.member.application.required.AccountNotice
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.domain.account.Account
import com.contentria.member.domain.account.AuthProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** docs/contexts/member/01-rules.md, 흐름 3. 수동 연동과 흐름 4의 연동 해제 */
@Service
class SocialCredentialService(
    private val accounts: AccountRepository,
    private val sessions: SessionService,
    private val events: ApplicationEventPublisher,
) : LinkSocialProviderUseCase, UnlinkSocialProviderUseCase, GetMyCredentialsQuery {

    /** `email_verified`를 보지 않는다. 로그인 상태이고 공급자 인증도 통과했다. */
    @Transactional
    override fun link(accountId: UUID, identity: VerifiedSocialIdentity) {
        // 1번: 다른 계정 소유면 실패. 같은 계정이면 아래 2번에 걸린다.
        val owner = accounts.findBySocialIdentity(identity.provider, identity.providerUserId)
        if (owner != null && owner.id != accountId) throw MemberUseCaseException.SocialIdentityLinkedElsewhere()

        // 2, 3번
        val account = load(accountId)
        account.linkSocialProvider(identity.provider, identity.providerUserId)
        events.publishEvent(AccountNoticeRequested(account.email, AccountNotice.SocialProviderLinked(identity.provider, automatic = false)))
    }

    /** Social providers only. Removing the password is not what "unlinking" means. */
    @Transactional
    override fun unlink(accountId: UUID, provider: AuthProvider): IssuedSession {
        require(!provider.usesPassword) { "$provider is not a social provider" }

        val account = load(accountId)
        account.unlinkProvider(provider)
        events.publishEvent(AccountNoticeRequested(account.email, AccountNotice.SocialProviderUnlinked(provider)))
        return sessions.restartFor(account)
    }

    @Transactional(readOnly = true)
    override fun get(accountId: UUID): Set<AuthProvider> =
        load(accountId).credentials.map { it.provider }.toSet()

    /** A valid access token whose account is gone reads as a session that no longer holds. */
    private fun load(accountId: UUID): Account =
        accounts.findById(accountId) ?: throw MemberUseCaseException.InvalidSession()
}
