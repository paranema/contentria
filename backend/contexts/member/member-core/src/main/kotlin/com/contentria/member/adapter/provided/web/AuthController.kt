package com.contentria.member.adapter.provided.web

import com.contentria.member.api.SessionCookieNames
import com.contentria.member.application.provided.CompleteEmailCodeLoginUseCase
import com.contentria.member.application.provided.CompleteEmailSignUpCommand
import com.contentria.member.application.provided.CompleteEmailSignUpUseCase
import com.contentria.member.application.provided.LoginWithPasswordUseCase
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.RefreshSessionUseCase
import com.contentria.member.application.provided.RequestPasswordResetLinkUseCase
import com.contentria.member.application.provided.ResetPasswordWithLinkUseCase
import com.contentria.member.application.provided.StartEmailCodeLoginUseCase
import com.contentria.member.application.provided.StartEmailSignUpUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val startSignUp: StartEmailSignUpUseCase,
    private val completeSignUp: CompleteEmailSignUpUseCase,
    private val loginWithPassword: LoginWithPasswordUseCase,
    private val startCodeLogin: StartEmailCodeLoginUseCase,
    private val completeCodeLogin: CompleteEmailCodeLoginUseCase,
    private val refreshSession: RefreshSessionUseCase,
    private val requestResetLink: RequestPasswordResetLinkUseCase,
    private val resetPassword: ResetPasswordWithLinkUseCase,
    private val cookies: SessionCookies,
) {

    @PostMapping("/sign-up/start")
    fun startSignUp(@Valid @RequestBody request: StartSignUpRequest): ResponseEntity<CodeSentResponse> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(CodeSentResponse(startSignUp.start(request.email)))

    @PostMapping("/sign-up/complete")
    fun completeSignUp(@Valid @RequestBody request: CompleteSignUpRequest): ResponseEntity<SessionResponse> {
        val session = completeSignUp.complete(CompleteEmailSignUpCommand(request.attemptId, request.code, request.password))
        return cookies.respond(ResponseEntity.status(HttpStatus.CREATED), session)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<SessionResponse> =
        cookies.respond(ResponseEntity.ok(), loginWithPassword.login(request.email, request.password))

    /** 202 whether or not an account uses the address (01-rules, 코드 로그인은 계정 존재를 알려주지 않는다). */
    @PostMapping("/login/code/start")
    fun startCodeLogin(@Valid @RequestBody request: StartCodeLoginRequest): ResponseEntity<CodeSentResponse> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(CodeSentResponse(startCodeLogin.start(request.email)))

    @PostMapping("/login/code/complete")
    fun completeCodeLogin(@Valid @RequestBody request: CompleteCodeLoginRequest): ResponseEntity<SessionResponse> =
        cookies.respond(ResponseEntity.ok(), completeCodeLogin.complete(request.attemptId, request.code))

    /** 202 whether or not an account uses the address, as for a code login. */
    @PostMapping("/password/reset-link")
    fun requestResetLink(@Valid @RequestBody request: PasswordResetLinkRequest): ResponseEntity<Void> {
        requestResetLink.requestResetLink(request.email)
        return ResponseEntity.accepted().build()
    }

    /** Spends a setup or reset link. The account's other sessions end; this one starts. */
    @PostMapping("/password/reset")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<SessionResponse> =
        cookies.respond(ResponseEntity.ok(), resetPassword.reset(request.token, request.newPassword))

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(name = SessionCookieNames.REFRESH_TOKEN, required = false) refreshToken: String?,
    ): ResponseEntity<SessionResponse> {
        val token = refreshToken ?: throw MemberUseCaseException.InvalidSession()
        return cookies.respond(ResponseEntity.ok(), refreshSession.refresh(token))
    }
}
