package com.contentria.member.application.service

import com.contentria.member.application.provided.IssuedSession
import com.contentria.member.application.provided.LoginWithPasswordUseCase
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.domain.account.AccountException
import com.contentria.member.domain.account.EmailAddresses
import com.contentria.member.domain.account.PasswordHasher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PasswordLoginService(
    private val accounts: AccountRepository,
    private val passwordHasher: PasswordHasher,
    private val sessions: SessionService,
) : LoginWithPasswordUseCase {

    /** 01-rules 공통: 미가입인지 비밀번호 오류인지 구분하지 않는다. 둘 다 InvalidCredentials. */
    @Transactional
    override fun login(email: String, rawPassword: String): IssuedSession {
        val account = runCatching { EmailAddresses.normalize(email) }.getOrNull()
            ?.let(accounts::findByEmail)
            ?: throw AccountException.InvalidCredentials()

        account.authenticateWithPassword(rawPassword, passwordHasher)
        return sessions.startFor(account)
    }
}
