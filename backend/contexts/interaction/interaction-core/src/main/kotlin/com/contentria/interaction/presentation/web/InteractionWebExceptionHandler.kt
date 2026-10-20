package com.contentria.interaction.presentation.web

import com.contentria.interaction.application.InteractionException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/** This context's failures as HTTP responses, for its own controllers only. */
@RestControllerAdvice(basePackageClasses = [SubscriptionLikeController::class])
class InteractionWebExceptionHandler {

    @ExceptionHandler
    fun handle(e: InteractionException): ResponseEntity<ErrorResponse> = when (e) {
        is InteractionException.NotAllowed -> error(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "This account cannot interact.")
        is InteractionException.BlogNotFound -> error(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "No blog has this address.")
        is InteractionException.PostNotFound -> error(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "No such post.")
        is InteractionException.OwnBlog -> error(HttpStatus.CONFLICT, "OWN_BLOG", "You cannot subscribe to your own blog.")
        is InteractionException.OwnPost -> error(HttpStatus.CONFLICT, "OWN_POST", "You cannot like your own post.")
    }

    @ExceptionHandler
    fun handle(e: TooManyIds): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "TOO_MANY_IDS", "Ask for at most $MAX_IDS ids at once.")

    @ExceptionHandler
    fun handle(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is not valid.")

    private fun error(status: HttpStatus, code: String, message: String) =
        ResponseEntity.status(status).body(ErrorResponse(code, message))
}

data class ErrorResponse(val code: String, val message: String)
