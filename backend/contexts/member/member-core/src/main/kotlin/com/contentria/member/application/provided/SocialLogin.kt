package com.contentria.member.application.provided

import com.contentria.member.domain.account.AuthProvider
import java.util.UUID

/**
 * Who a social provider says the caller is, read from an ID token whose signature, issuer,
 * audience, expiry and nonce have already been checked (01-rules 흐름 2의 1번, 어댑터).
 *
 * [emailVerified] is false when the claim is missing.
 */
data class VerifiedSocialIdentity(
    val provider: AuthProvider,
    val providerUserId: String,
    val email: String?,
    val emailVerified: Boolean,
)

/** docs/contexts/member/01-rules.md, 흐름 2. 소셜 로그인 (가입 겸용) */
interface LoginWithSocialProviderUseCase {
    fun login(identity: VerifiedSocialIdentity): IssuedSession
}

/** 흐름 3. 수동 연동. The caller is signed in as [accountId]; no account is ever created here. */
interface LinkSocialProviderUseCase {
    fun link(accountId: UUID, identity: VerifiedSocialIdentity)
}

/** 흐름 4. 연동 해제. Ends every other session and returns a new one for the caller (01-rules 공통). */
interface UnlinkSocialProviderUseCase {
    fun unlink(accountId: UUID, provider: AuthProvider): IssuedSession
}

interface GetMyCredentialsQuery {
    /** The providers the account can sign in with, EMAIL included when it has a password. */
    fun get(accountId: UUID): Set<AuthProvider>
}
