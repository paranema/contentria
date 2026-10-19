package com.contentria.member.application.required

import com.contentria.member.domain.account.Role
import java.time.Instant
import java.util.UUID

/** Delivers a verification code to an address. The code never appears anywhere else. */
interface VerificationCodeSender {
    fun send(email: String, code: String)
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
