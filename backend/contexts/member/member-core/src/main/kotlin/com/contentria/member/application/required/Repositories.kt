package com.contentria.member.application.required

import com.contentria.member.domain.account.Account
import com.contentria.member.domain.account.AuthProvider
import com.contentria.member.domain.emailchange.EmailChangeAttempt
import com.contentria.member.domain.login.LoginAttempt
import com.contentria.member.domain.password.PasswordToken
import com.contentria.member.domain.profile.Profile
import com.contentria.member.domain.session.RefreshToken
import com.contentria.member.domain.signup.SignUpAttempt
import com.contentria.member.domain.withdrawal.WithdrawalAttempt
import org.springframework.data.repository.Repository
import java.util.UUID

// Spring Data marker repositories (docs/architecture/02-internal-architecture.md, 리포지토리).
// Only the methods declared here exist.

interface AccountRepository : Repository<Account, UUID> {
    fun save(account: Account): Account
    fun findById(id: UUID): Account?
    fun findByIdIn(ids: Collection<UUID>): List<Account>
    fun findByEmail(email: String): Account?

    /** The account that owns the social identity. The query is [Account]'s named query of the same name. */
    fun findBySocialIdentity(provider: AuthProvider, providerUserId: String): Account?
}

interface ProfileRepository : Repository<Profile, UUID> {
    fun save(profile: Profile): Profile
    fun findById(id: UUID): Profile?
    fun findByIdIn(ids: Collection<UUID>): List<Profile>
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
    fun deleteByAccountId(accountId: UUID)
}

interface PasswordTokenRepository : Repository<PasswordToken, UUID> {
    fun save(token: PasswordToken): PasswordToken
    fun findByTokenHash(tokenHash: String): PasswordToken?
    fun deleteByAccountId(accountId: UUID)
}

interface EmailChangeAttemptRepository : Repository<EmailChangeAttempt, UUID> {
    fun save(attempt: EmailChangeAttempt): EmailChangeAttempt
    fun findById(id: UUID): EmailChangeAttempt?
    fun deleteByAccountId(accountId: UUID)
    fun delete(attempt: EmailChangeAttempt)
}

interface WithdrawalAttemptRepository : Repository<WithdrawalAttempt, UUID> {
    fun save(attempt: WithdrawalAttempt): WithdrawalAttempt
    fun findById(id: UUID): WithdrawalAttempt?
    fun deleteByAccountId(accountId: UUID)
    fun delete(attempt: WithdrawalAttempt)
}
