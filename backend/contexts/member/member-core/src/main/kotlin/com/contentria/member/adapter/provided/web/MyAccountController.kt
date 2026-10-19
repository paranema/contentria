package com.contentria.member.adapter.provided.web

import com.contentria.member.application.provided.ChangePasswordUseCase
import com.contentria.member.application.provided.CompleteEmailChangeUseCase
import com.contentria.member.application.provided.CompleteWithdrawalUseCase
import com.contentria.member.application.provided.RequestPasswordSetupLinkUseCase
import com.contentria.member.application.provided.StartEmailChangeUseCase
import com.contentria.member.application.provided.StartWithdrawalUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * docs/contexts/member/01-rules.md, 흐름 4. 계정 관리, for the signed-in account. Everything that
 * changes how the account is reached answers with a new session: the others have ended.
 */
@RestController
@RequestMapping("/me")
class MyAccountController(
    private val changePassword: ChangePasswordUseCase,
    private val requestSetupLink: RequestPasswordSetupLinkUseCase,
    private val startEmailChange: StartEmailChangeUseCase,
    private val completeEmailChange: CompleteEmailChangeUseCase,
    private val startWithdrawal: StartWithdrawalUseCase,
    private val completeWithdrawal: CompleteWithdrawalUseCase,
    private val cookies: SessionCookies,
) {

    @PostMapping("/password")
    fun changePassword(
        @AuthenticationPrincipal accountId: UUID,
        @Valid @RequestBody request: ChangePasswordRequest,
    ): ResponseEntity<SessionResponse> =
        cookies.respond(ResponseEntity.ok(), changePassword.change(accountId, request.currentPassword, request.newPassword))

    /** Social-only accounts. The password itself is set through the mailed link. */
    @PostMapping("/password/setup-link")
    fun requestSetupLink(@AuthenticationPrincipal accountId: UUID): ResponseEntity<Void> {
        requestSetupLink.requestSetupLink(accountId)
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/email-change/start")
    fun startEmailChange(
        @AuthenticationPrincipal accountId: UUID,
        @Valid @RequestBody request: StartEmailChangeRequest,
    ): ResponseEntity<CodeSentResponse> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(CodeSentResponse(startEmailChange.start(accountId, request.newEmail)))

    @PostMapping("/email-change/complete")
    fun completeEmailChange(
        @AuthenticationPrincipal accountId: UUID,
        @Valid @RequestBody request: CompleteEmailChangeRequest,
    ): ResponseEntity<SessionResponse> {
        val session = completeEmailChange.complete(accountId, request.attemptId, request.currentAddressCode, request.newAddressCode)
        return cookies.respond(ResponseEntity.ok(), session)
    }

    @PostMapping("/withdrawal/start")
    fun startWithdrawal(@AuthenticationPrincipal accountId: UUID): ResponseEntity<CodeSentResponse> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(CodeSentResponse(startWithdrawal.start(accountId)))

    /** Every session ends, this one included; the cookies are expired in the same response. */
    @PostMapping("/withdrawal/complete")
    fun completeWithdrawal(
        @AuthenticationPrincipal accountId: UUID,
        @Valid @RequestBody request: CompleteWithdrawalRequest,
    ): ResponseEntity<Void> {
        completeWithdrawal.complete(accountId, request.attemptId, request.code)
        return cookies.cleared(ResponseEntity.status(HttpStatus.NO_CONTENT))
    }
}
