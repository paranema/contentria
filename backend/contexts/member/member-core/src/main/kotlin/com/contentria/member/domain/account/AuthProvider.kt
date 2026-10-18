package com.contentria.member.domain.account

enum class AuthProvider {

    /** Email and password. The only provider that stores a password hash. */
    EMAIL,

    GOOGLE;

    val usesPassword: Boolean get() = this == EMAIL
}
