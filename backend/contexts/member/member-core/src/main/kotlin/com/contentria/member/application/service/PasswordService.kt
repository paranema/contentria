package com.contentria.member.application.service

import com.contentria.member.application.provided.ChangePasswordUseCase
import com.contentria.member.application.provided.IssuedSession
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.RequestPasswordResetLinkUseCase
import com.contentria.member.application.provided.RequestPasswordSetupLinkUseCase
import com.contentria.member.application.provided.ResetPasswordWithLinkUseCase
import com.contentria.member.application.required.AccountNotice
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.application.required.PasswordLinkPurpose
import com.contentria.member.application.required.PasswordLinkSender
import com.contentria.member.application.required.PasswordTokenRepository
import com.contentria.member.domain.account.Account
import com.contentria.member.domain.account.AccountException
import com.contentria.member.domain.account.AuthProvider
import com.contentria.member.domain.account.EmailAddresses
import com.contentria.member.domain.account.PasswordHasher
import com.contentria.member.domain.password.PasswordToken
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** docs/contexts/member/01-rules.md, 흐름 4의 비밀번호 최초 설정, 변경, 분실 */
@Service
class PasswordService(
    private val accounts: AccountRepository,
    private val tokens: PasswordTokenRepository,
    private val hasher: PasswordHasher,
    private val linkSender: PasswordLinkSender,
    private val sessions: SessionService,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) : ChangePasswordUseCase, RequestPasswordSetupLinkUseCase, RequestPasswordResetLinkUseCase, ResetPasswordWithLinkUseCase {

    @Transactional
    override fun change(accountId: UUID, currentPassword: String, newPassword: String): IssuedSession {
        val account = accounts.loadActive(accountId)
        if (!account.hasCredentialFor(AuthProvider.EMAIL)) throw MemberUseCaseException.PasswordNotSet()

        try {
            account.authenticateWithPassword(currentPassword, hasher)
        } catch (e: AccountException.InvalidCredentials) {
            throw MemberUseCaseException.CurrentPasswordMismatch()
        }
        return setPassword(account, newPassword)
    }

    /** 링크를 통해서만. A signed-in session alone does not add a password. */
    @Transactional
    override fun requestSetupLink(accountId: UUID) {
        val account = accounts.loadActive(accountId)
        if (account.hasCredentialFor(AuthProvider.EMAIL)) throw MemberUseCaseException.PasswordAlreadySet()
        sendLink(account, PasswordLinkPurpose.SETUP)
    }

    /** No link for an unknown address or an account that cannot sign in; the caller cannot tell. */
    @Transactional
    override fun requestResetLink(email: String) {
        val account = accounts.findByEmail(EmailAddresses.normalize(email))
            ?.takeIf { it.status.canAuthenticate }
            ?: return
        sendLink(account, PasswordLinkPurpose.RESET)
    }

    @Transactional
    override fun reset(token: String, newPassword: String): IssuedSession {
        val stored = tokens.findByTokenHash(PasswordToken.hashOf(token))
            ?.takeIf { it.isUsableAt(clock.instant()) }
            ?: throw MemberUseCaseException.InvalidPasswordToken()
        val account = accounts.findById(stored.accountId)
            ?.takeIf { it.status.canAuthenticate }
            ?: throw MemberUseCaseException.InvalidPasswordToken()
        return setPassword(account, newPassword)
    }

    /** Spends every outstanding link, the one just used included, and ends the other sessions. */
    private fun setPassword(account: Account, newPassword: String): IssuedSession {
        account.setPassword(hasher.hash(newPassword))
        tokens.deleteByAccountId(account.id)
        events.publishEvent(AccountNoticeRequested(account.email, AccountNotice.PasswordChanged))
        return sessions.restartFor(account)
    }

    /** A new link makes every earlier one useless. */
    private fun sendLink(account: Account, purpose: PasswordLinkPurpose) {
        tokens.deleteByAccountId(account.id)
        val issued = PasswordToken.issue(account.id, clock.instant())
        tokens.save(issued.token)
        linkSender.send(account.email, issued.rawValue, purpose)
    }
}
