package com.contentria.member.application.provided

import java.util.UUID

/** docs/contexts/member/01-rules.md, 흐름 1. 로컬 회원가입 */
interface StartEmailSignUpUseCase {
    /** Sends a code to [email]. Returns the attempt id the requester must present with it. */
    fun start(email: String): UUID
}

interface CompleteEmailSignUpUseCase {
    /** Creates the account and its profile once the code checks out, and starts a session. */
    fun complete(command: CompleteEmailSignUpCommand): IssuedSession
}

data class CompleteEmailSignUpCommand(
    val attemptId: UUID,
    val code: String,
    val rawPassword: String,
)
