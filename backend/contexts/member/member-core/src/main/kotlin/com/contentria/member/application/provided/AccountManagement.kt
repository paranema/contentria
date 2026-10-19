package com.contentria.member.application.provided

import java.util.UUID

// docs/contexts/member/01-rules.md, 흐름 4. 계정 관리
// Every use case that changes how the account is reached ends the account's other sessions and
// returns a new one for the caller (01-rules 공통, 세션 무효화).

interface ChangePasswordUseCase {
    /** 현재 비밀번호 확인 후 변경 */
    fun change(accountId: UUID, currentPassword: String, newPassword: String): IssuedSession
}

interface RequestPasswordSetupLinkUseCase {
    /** 비밀번호 최초 설정 (소셜 전용 계정): 계정 이메일로 설정 링크 */
    fun requestSetupLink(accountId: UUID)
}

interface RequestPasswordResetLinkUseCase {
    /** 비밀번호 분실: 계정 이메일로 재설정 링크. Answers the same whether or not an account uses [email]. */
    fun requestResetLink(email: String)
}

interface ResetPasswordWithLinkUseCase {
    /** Spends a setup or reset link's token. */
    fun reset(token: String, newPassword: String): IssuedSession
}

interface StartEmailChangeUseCase {
    /** Sends one code to the current address and one to [newEmail]. Returns the attempt id. */
    fun start(accountId: UUID, newEmail: String): UUID
}

interface CompleteEmailChangeUseCase {
    fun complete(accountId: UUID, attemptId: UUID, currentAddressCode: String, newAddressCode: String): IssuedSession
}

interface StartWithdrawalUseCase {
    /** Sends a code to the account's address. Returns the attempt id. */
    fun start(accountId: UUID): UUID
}

interface CompleteWithdrawalUseCase {
    /** Closes the account and marks its profile withdrawn. Every session ends, the caller's included. */
    fun complete(accountId: UUID, attemptId: UUID, code: String)
}
