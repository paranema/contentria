package com.contentria.member.application.required

import com.contentria.member.domain.account.Account
import com.contentria.member.domain.account.AuthProvider
import com.contentria.member.domain.login.LoginAttempt
import com.contentria.member.domain.profile.Profile
import com.contentria.member.domain.session.RefreshToken
import com.contentria.member.domain.signup.SignUpAttempt
import org.springframework.data.repository.Repository
import java.util.UUID

// Spring Data marker repositories (docs/architecture/02-internal-architecture.md, 리포지토리).
// Only the methods declared here exist.

interface AccountRepository : Repository<Account, UUID> {
    fun save(account: Account): Account
    fun findById(id: UUID): Account?
    fun findByEmail(email: String): Account?

    /** The account that owns the social identity. The query is [Account]'s named query of the same name. */
    fun findBySocialIdentity(provider: AuthProvider, providerUserId: String): Account?
}

interface ProfileRepository : Repository<Profile, UUID> {
    fun save(profile: Profile): Profile
    fun findById(id: UUID): Profile?
    fun existsByDisplayName(displayName: String): Boolean
}

interface SignUpAttemptRepository : Repository<SignUpAttempt, UUID> {
    fun save(attempt: SignUpAttempt): SignUpAttempt
    fun findById(id: UUID): SignUpAttempt?
    fun deleteByEmail(email: String)
    fun delete(attempt: SignUpAttempt)
}

interface LoginAttemptRepository : Repository<LoginAttempt, UUID> {
    fun save(attempt: LoginAttempt): LoginAttempt
    fun findById(id: UUID): LoginAttempt?
    fun deleteByEmail(email: String)
    fun delete(attempt: LoginAttempt)
}

interface RefreshTokenRepository : Repository<RefreshToken, UUID> {
    fun save(token: RefreshToken): RefreshToken
    fun findByTokenHash(tokenHash: String): RefreshToken?
    fun delete(token: RefreshToken)
}
