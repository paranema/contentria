package com.contentria.member.application.required

import com.contentria.member.domain.account.AuthProvider
import com.contentria.member.domain.account.Role
import java.time.Instant
import java.util.UUID

/** Delivers a verification code to an address. The code never appears anywhere else. */
interface VerificationCodeSender {
    fun send(email: String, code: String, purpose: CodePurpose)
}

/**
 * What the code will do once returned. The message says so, so that someone who receives a
 * login code they did not ask for knows not to hand it over.
 */
enum class CodePurpose {
    SIGN_UP,
    LOGIN,

    /** Sent to the current address: the owner confirms the change (01-rules 흐름 4, 이메일 변경). */
    EMAIL_CHANGE_CURRENT,

    /** Sent to the new address: it proves the address. */
    EMAIL_CHANGE_NEW,

    WITHDRAWAL,
}

/** Delivers a password setup or reset link carrying [token]. The link's address is the adapter's concern. */
interface PasswordLinkSender {
    fun send(email: String, token: String, purpose: PasswordLinkPurpose)
}

enum class PasswordLinkPurpose {
    /** First password on a social-only account, asked for while signed in. */
    SETUP,

    /** Forgotten password, asked for without signing in. */
    RESET,
}

/**
 * Tells an account's owner, at the account's address, that a way into the account was added or
 * removed (docs/contexts/member/02-implementation.md, 이벤트). Sent after commit: a failed send
 * does not undo the change.
 */
interface AccountNoticeSender {
    fun send(email: String, notice: AccountNotice)
}

sealed interface AccountNotice {
    /** [automatic] is a link made during a social sign-in because the addresses matched (흐름 2의 7번). */
    data class SocialProviderLinked(val provider: AuthProvider, val automatic: Boolean) : AccountNotice
    data class SocialProviderUnlinked(val provider: AuthProvider) : AccountNotice

    /** Set, changed or reset. */
    data object PasswordChanged : AccountNotice

    /** Sent to the previous address once the account has moved off it. */
    data object EmailChanged : AccountNotice
}

/** Produces verification codes from a cryptographically secure source. */
interface VerificationCodeGenerator {
    fun generate(): String
}

/** Signs and verifies access tokens. Stateless: verifying one needs no storage. */
interface AccessTokenCodec {
    fun issue(accountId: UUID, roles: Set<Role>, issuedAt: Instant): IssuedAccessToken
    fun verify(token: String, now: Instant): VerifiedAccessToken?
}

data class IssuedAccessToken(val value: String, val expiresAt: Instant)

data class VerifiedAccessToken(val accountId: UUID, val roles: Set<Role>)
