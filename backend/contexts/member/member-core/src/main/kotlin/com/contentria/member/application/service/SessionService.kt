package com.contentria.member.application.service

import com.contentria.member.application.provided.IssuedSession
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.RefreshSessionUseCase
import com.contentria.member.application.required.AccessTokenCodec
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.application.required.RefreshTokenRepository
import com.contentria.member.domain.account.Account
import com.contentria.member.domain.session.RefreshToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Starts and rotates sessions (docs/conventions/security.md, Refresh Token Rotation).
 *
 * A presented refresh token is deleted before a new one is issued, so a token that has already
 * been rotated is simply unknown. Concurrent refreshes with the same token are not given a grace
 * window yet; the second one fails (docs/design-notes/refresh-token-grace-window.md describes the
 * legacy approach).
 */
@Service
class SessionService(
    private val accounts: AccountRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val accessTokens: AccessTokenCodec,
    private val policy: SessionPolicy,
    private val clock: Clock,
) : RefreshSessionUseCase {

    /** Called inside the caller's transaction, after the account has been authenticated. */
    fun startFor(account: Account): IssuedSession {
        val now = clock.instant()
        val access = accessTokens.issue(account.id, account.roles, now)
        val refresh = RefreshToken.issue(account.id, now, policy.refreshTokenTtl)
        refreshTokens.save(refresh.token)
        return IssuedSession(
            accountId = account.id,
            accessToken = access.value,
            accessTokenExpiresAt = access.expiresAt,
            refreshToken = refresh.rawValue,
            refreshTokenExpiresAt = refresh.token.expiresAt,
        )
    }

    @Transactional
    override fun refresh(refreshToken: String): IssuedSession {
        val stored = refreshTokens.findByTokenHash(RefreshToken.hashOf(refreshToken))
            ?: throw MemberUseCaseException.InvalidSession()
        refreshTokens.delete(stored)

        if (!stored.isUsableAt(clock.instant())) throw MemberUseCaseException.InvalidSession()

        val account = accounts.findById(stored.accountId)
            ?.takeIf { it.status.canAuthenticate }
            ?: throw MemberUseCaseException.InvalidSession()

        return startFor(account)
    }
}
