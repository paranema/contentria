package com.contentria.member.adapter.provided.web

import com.contentria.member.api.SessionCookieNames
import com.contentria.member.application.provided.CompleteEmailSignUpCommand
import com.contentria.member.application.provided.CompleteEmailSignUpUseCase
import com.contentria.member.application.provided.LoginWithPasswordUseCase
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.RefreshSessionUseCase
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
    private val refreshSession: RefreshSessionUseCase,
    private val cookies: SessionCookies,
) {

    @PostMapping("/sign-up/start")
    fun startSignUp(@Valid @RequestBody request: StartSignUpRequest): ResponseEntity<StartSignUpResponse> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(StartSignUpResponse(startSignUp.start(request.email)))

    @PostMapping("/sign-up/complete")
    fun completeSignUp(@Valid @RequestBody request: CompleteSignUpRequest): ResponseEntity<SessionResponse> {
        val session = completeSignUp.complete(CompleteEmailSignUpCommand(request.attemptId, request.code, request.password))
        return cookies.respond(ResponseEntity.status(HttpStatus.CREATED), session)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<SessionResponse> =
        cookies.respond(ResponseEntity.ok(), loginWithPassword.login(request.email, request.password))

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(name = SessionCookieNames.REFRESH_TOKEN, required = false) refreshToken: String?,
    ): ResponseEntity<SessionResponse> {
        val token = refreshToken ?: throw MemberUseCaseException.InvalidSession()
        return cookies.respond(ResponseEntity.ok(), refreshSession.refresh(token))
    }
}
