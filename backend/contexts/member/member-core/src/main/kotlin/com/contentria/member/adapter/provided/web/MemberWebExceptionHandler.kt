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
        is AccountException.LastCredentialRemoval -> error(HttpStatus.CONFLICT, "LAST_CREDENTIAL", "The account would have no way to sign in.")
        is AccountException.ProviderNotLinked -> error(HttpStatus.NOT_FOUND, "PROVIDER_NOT_LINKED", "That provider is not linked.")
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
        // Social failures normally arrive as a redirect from the callback; these cover a direct call.
        is MemberUseCaseException.SocialEmailMissing ->
            error(HttpStatus.BAD_REQUEST, "SOCIAL_EMAIL_MISSING", "The provider did not share an email address.")
        is MemberUseCaseException.SocialIdentityLinkedElsewhere ->
            error(HttpStatus.CONFLICT, "SOCIAL_IDENTITY_TAKEN", "That account is linked to another member.")
        is MemberUseCaseException.CurrentPasswordMismatch ->
            error(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_MISMATCH", "The current password does not match.")
        is MemberUseCaseException.PasswordNotSet ->
            error(HttpStatus.CONFLICT, "PASSWORD_NOT_SET", "This account has no password. Use a setup link.")
        is MemberUseCaseException.PasswordAlreadySet ->
            error(HttpStatus.CONFLICT, "PASSWORD_ALREADY_SET", "This account already has a password.")
        is MemberUseCaseException.InvalidPasswordToken ->
            error(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_TOKEN", "The link is not valid. Ask for a new one.")
        is MemberUseCaseException.EmailInUse ->
            error(HttpStatus.CONFLICT, "EMAIL_IN_USE", "Another account uses this email address.")
        is MemberUseCaseException.EmailUnchanged ->
            error(HttpStatus.BAD_REQUEST, "EMAIL_UNCHANGED", "That is already the account's address.")
        is MemberUseCaseException.DisplayNameTaken ->
            error(HttpStatus.CONFLICT, "DISPLAY_NAME_TAKEN", "Another member already uses this display name.")
        // The frontend shows the checkbox on the first and tries again with its token.
        is MemberUseCaseException.HumanCheckRequired ->
            error(HttpStatus.FORBIDDEN, "RECAPTCHA_CHALLENGE_REQUIRED", "Confirm you are not a robot.")
        is MemberUseCaseException.HumanCheckFailed ->
            error(HttpStatus.FORBIDDEN, "RECAPTCHA_FAILED", "The check did not pass. Try again.")
        is MemberUseCaseException.HumanCheckUnavailable ->
            error(HttpStatus.SERVICE_UNAVAILABLE, "RECAPTCHA_UNAVAILABLE", "Try again in a moment.")
        is MemberUseCaseException.InvalidSession ->
            error(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "Sign in again.")
    }

    @ExceptionHandler
    fun handle(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is not valid.")

    private fun error(status: HttpStatus, code: String, message: String) =
        ResponseEntity.status(status).body(ErrorResponse(code, message))
}
