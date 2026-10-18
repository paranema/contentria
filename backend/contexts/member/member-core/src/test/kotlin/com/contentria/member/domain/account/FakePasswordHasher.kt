package com.contentria.member.domain.account

/**
 * Deterministic stand-in for the real hasher, so tests assert on the aggregate's behaviour
 * rather than on BCrypt's output. Reversible on purpose — it is never used outside tests.
 */
class FakePasswordHasher : PasswordHasher {

    override fun hash(rawPassword: String): String = "hashed:$rawPassword"

    override fun matches(rawPassword: String, passwordHash: String): Boolean =
        passwordHash == hash(rawPassword)
}
