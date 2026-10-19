package com.contentria.member.application.provided

import java.util.UUID

/** docs/contexts/member/01-rules.md, 흐름 6. 이메일 코드 로그인 */
interface StartEmailCodeLoginUseCase {
    /**
     * Sends a login code to [email] if an account uses it. Returns an attempt id either way,
     * so the answer does not tell whether the address is registered.
     */
    fun start(email: String): UUID
}

interface CompleteEmailCodeLoginUseCase {
    /** Starts a session once the code checks out and the account may sign in. */
    fun complete(attemptId: UUID, code: String): IssuedSession
}
