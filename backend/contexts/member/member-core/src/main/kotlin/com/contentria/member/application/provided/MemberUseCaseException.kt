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

    /** 흐름 4 비밀번호 변경: 현재 비밀번호가 틀렸다. 로그인 실패와 달리 세션은 그대로다. */
    class CurrentPasswordMismatch :
        MemberUseCaseException("The current password does not match")

    /** 비밀번호가 없는 계정(소셜 전용)의 변경 요청. 설정 링크를 써야 한다. */
    class PasswordNotSet :
        MemberUseCaseException("This account has no password yet")

    /** 비밀번호가 있는 계정의 설정 링크 요청. 변경을 써야 한다. */
    class PasswordAlreadySet :
        MemberUseCaseException("This account already has a password")

    /** Unknown, expired or already spent link token, or an account it can no longer act on. */
    class InvalidPasswordToken :
        MemberUseCaseException("The link is not valid")

    /** 이메일 변경: 다른 계정이 쓰는 주소다. 가입과 달리 가입 수단은 알려주지 않는다. */
    class EmailInUse :
        MemberUseCaseException("Another account uses this email address")

    class EmailUnchanged :
        MemberUseCaseException("The new address is the current one")

    class DisplayNameTaken :
        MemberUseCaseException("Another member already uses this display name")

    /** Unknown, expired or already rotated refresh token, or an account that can no longer sign in. */
    class InvalidSession :
        MemberUseCaseException("The session is no longer valid")
}
