package com.contentria.member.domain.account

/**
 * Password hashing, declared by the domain and implemented by an outbound adapter
 * (Spring Security's `PasswordEncoder`).
 *
 * It lives in `domain` rather than in the application layer's outbound ports because
 * [Account] itself calls it. That is what lets a raw password be verified without ever
 * being read out of the aggregate: callers hand the raw value in, and only a boolean or an
 * exception comes back.
 */
interface PasswordHasher {

    fun hash(rawPassword: String): String

    fun matches(rawPassword: String, passwordHash: String): Boolean
}
