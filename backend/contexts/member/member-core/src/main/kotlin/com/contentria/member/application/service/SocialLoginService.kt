package com.contentria.member.application.service

import com.contentria.member.application.provided.IssuedSession
import com.contentria.member.application.provided.LoginWithSocialProviderUseCase
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.VerifiedSocialIdentity
import com.contentria.member.application.required.AccountNotice
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.domain.account.Account
import com.contentria.member.domain.account.EmailAddresses
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** docs/contexts/member/01-rules.md, 흐름 2. 소셜 로그인 (가입 겸용) */
@Service
class SocialLoginService(
    private val accounts: AccountRepository,
    private val profiles: ProfileService,
    private val sessions: SessionService,
    private val events: ApplicationEventPublisher,
) : LoginWithSocialProviderUseCase {

    @Transactional
    override fun login(identity: VerifiedSocialIdentity): IssuedSession {
        val provider = identity.provider
        val providerUserId = identity.providerUserId

        // 2번: 신원은 공급자 사용자 ID로만 판단한다. email_verified는 보지 않는다.
        accounts.findBySocialIdentity(provider, providerUserId)?.let { account ->
            account.authenticateWithSocialProvider(provider, providerUserId)
            return sessions.startFor(account)
        }

        // 3번
        val email = identity.email ?: throw MemberUseCaseException.SocialEmailMissing()

        val sameAddress = accounts.findByEmail(EmailAddresses.normalize(email))
        if (sameAddress == null) {
            // 4, 5번: an address the provider did not verify is refused before anything is created
            val account = accounts.save(Account.registerWithSocialProvider(email, provider, providerUserId, identity.emailVerified))
            profiles.createFor(account.id)
            return sessions.startFor(account)
        }

        // 4, 6, 7번: refused if the provider did not verify the address, then if the account is not ACTIVE
        sameAddress.autoLinkSocialProvider(provider, providerUserId, email, identity.emailVerified)
        events.publishEvent(AccountNoticeRequested(sameAddress.email, AccountNotice.SocialProviderLinked(provider, automatic = true)))
        return sessions.startFor(sameAddress)
    }
}
