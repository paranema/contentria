package com.contentria.notification.presentation.web

import com.contentria.notification.application.NotificationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/** This context's failures as HTTP responses, for its own controllers only. */
@RestControllerAdvice(basePackageClasses = [NotificationController::class])
class NotificationWebExceptionHandler {

    @ExceptionHandler
    fun handle(e: NotificationException): ResponseEntity<ErrorResponse> = when (e) {
        is NotificationException.NotFound -> error(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "No such notification.")
        is NotificationException.InvalidCursor -> error(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "The page cursor is not valid.")
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun invalidRequest(): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is not valid.")

    private fun error(status: HttpStatus, code: String, message: String) =
        ResponseEntity.status(status).body(ErrorResponse(code, message))
}

data class ErrorResponse(val code: String, val message: String)
