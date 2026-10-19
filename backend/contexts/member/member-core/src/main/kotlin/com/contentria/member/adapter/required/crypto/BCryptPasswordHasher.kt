package com.contentria.member.adapter.required.crypto

import com.contentria.member.domain.account.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/** docs/conventions/security.md, Password Handling: BCrypt. */
@Component
class BCryptPasswordHasher : PasswordHasher {

    private val encoder = BCryptPasswordEncoder()

    override fun hash(rawPassword: String): String = encoder.encode(rawPassword)!!

    override fun matches(rawPassword: String, passwordHash: String): Boolean =
        encoder.matches(rawPassword, passwordHash)
}
