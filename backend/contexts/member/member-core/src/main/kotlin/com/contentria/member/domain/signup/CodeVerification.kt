package com.contentria.member.domain.signup

enum class CodeVerification {
    VERIFIED,

    /** Wrong code, attempts left. */
    MISMATCH,

    /** Too many wrong codes. The attempt is dead; the owner must ask for a new code. */
    LOCKED,

    EXPIRED,
}
