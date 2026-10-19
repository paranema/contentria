package com.contentria.member.adapter.provided.web

import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.domain.account.AccountException
import com.contentria.member.domain.profile.ProfileException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Turns this context's failures into HTTP responses for its own controllers only. Messages are
 * generic (docs/conventions/security.md, Error Responses): nothing here repeats an address or a
 * value the client sent.
 */
@RestControllerAdvice(basePackageClasses = [AuthController::class])
class MemberWebExceptionHandler {

    @ExceptionHandler
    fun handle(e: AccountException): ResponseEntity<ErrorResponse> = when (e) {
        is AccountException.InvalidCredentials -> error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password does not match.")
        is AccountException.NotAuthenticatable -> error(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "This account cannot sign in.")
        is AccountException.InvalidEmail -> error(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Not a usable email address.")
        else -> error(HttpStatus.BAD_REQUEST, "ACCOUNT_RULE_VIOLATED", "The request breaks an account rule.")
    }

    @ExceptionHandler
    fun handle(e: ProfileException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_PROFILE", "The profile value is not allowed.")

    @ExceptionHandler
    fun handle(e: MemberUseCaseException): ResponseEntity<ErrorResponse> = when (e) {
        is MemberUseCaseException.EmailAlreadyRegistered -> ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("EMAIL_ALREADY_REGISTERED", "An account already uses this email address.", e.providers))
        is MemberUseCaseException.VerificationFailed ->
            error(HttpStatus.BAD_REQUEST, "VERIFICATION_${e.reason}", "The code could not be verified.")
        is MemberUseCaseException.DisplayNameTaken ->
            error(HttpStatus.CONFLICT, "DISPLAY_NAME_TAKEN", "Another member already uses this display name.")
        is MemberUseCaseException.InvalidSession ->
            error(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "Sign in again.")
    }

    @ExceptionHandler
    fun handle(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is not valid.")

    private fun error(status: HttpStatus, code: String, message: String) =
        ResponseEntity.status(status).body(ErrorResponse(code, message))
}
