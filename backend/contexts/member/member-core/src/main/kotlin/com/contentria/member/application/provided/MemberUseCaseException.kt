package com.contentria.member.application.provided

import com.contentria.shared.exception.DomainException

/** Failures decided by a use case rather than by a single aggregate. */
sealed class MemberUseCaseException(message: String) : DomainException(message) {

    /** 01-rules 공통: 가입 시 중복은 409로 알리고, 어떤 공급자로 가입됐는지도 알려준다. */
    class EmailAlreadyRegistered(val providers: Set<String>) :
        MemberUseCaseException("An account already uses this email address")

    class VerificationFailed(val reason: Reason) :
        MemberUseCaseException("The code could not be verified: $reason") {
        enum class Reason { MISMATCH, LOCKED, EXPIRED }
    }

    /** 01-rules 흐름 2의 3번: 공급자가 이메일 클레임을 주지 않았다. 공급자 화면에서 동의하도록 안내한다. */
    class SocialEmailMissing :
        MemberUseCaseException("The provider did not share an email address")

    /** 01-rules 흐름 3의 1번: 그 소셜 신원은 다른 계정 소유다. 계정을 합치지 않는다. */
    class SocialIdentityLinkedElsewhere :
        MemberUseCaseException("This social identity belongs to another account")

    class DisplayNameTaken :
        MemberUseCaseException("Another member already uses this display name")

    /** Unknown, expired or already rotated refresh token, or an account that can no longer sign in. */
    class InvalidSession :
        MemberUseCaseException("The session is no longer valid")
}
