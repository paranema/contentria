package com.contentria.member.application.provided

/**
 * docs/contexts/member/01-rules.md, 사람 확인. Asked before anything that sends mail or tries a
 * password, so that a script can neither flood addresses with codes nor guess passwords.
 */
interface CheckHumanUseCase {
    /**
     * Passes, or throws [MemberUseCaseException.HumanCheckRequired] (show the checkbox and try
     * again), [MemberUseCaseException.HumanCheckFailed] or [MemberUseCaseException.HumanCheckUnavailable].
     */
    fun check(proof: HumanProof, action: HumanAction)
}

/** What the browser sends: the invisible check's token, or the checkbox's once asked for it. */
data class HumanProof(val v3Token: String?, val v2Token: String?)

/** The action the invisible check was run for; a token made for another action does not count. */
enum class HumanAction(val value: String) {
    SIGN_UP("sign_up"),
    LOGIN("login"),
    LOGIN_CODE("login_code"),
    PASSWORD_RESET("password_reset"),
}
