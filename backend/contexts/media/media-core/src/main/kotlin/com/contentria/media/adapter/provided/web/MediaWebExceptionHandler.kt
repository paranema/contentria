package com.contentria.media.adapter.provided.web

import com.contentria.media.application.provided.MediaUseCaseException
import com.contentria.media.domain.image.MediaException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/** This context's failures as HTTP responses, for its own controllers only. Messages never echo input. */
@RestControllerAdvice(basePackageClasses = [ImageUploadController::class])
class MediaWebExceptionHandler {

    @ExceptionHandler
    fun handle(e: MediaUseCaseException): ResponseEntity<ErrorResponse> = when (e) {
        is MediaUseCaseException.ImageNotFound -> error(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "No such image.")
        is MediaUseCaseException.DailyLimitReached -> error(HttpStatus.TOO_MANY_REQUESTS, "DAILY_UPLOAD_LIMIT", "You can upload 100MB of images a day.")
        is MediaUseCaseException.UploadMissing -> error(HttpStatus.CONFLICT, "UPLOAD_NOT_FINISHED", "Put the file to the upload address first.")
        is MediaUseCaseException.ContentMismatch -> error(HttpStatus.BAD_REQUEST, "IMAGE_CONTENT_MISMATCH", "The file is not the declared image format.")
    }

    @ExceptionHandler
    fun handle(e: MediaException): ResponseEntity<ErrorResponse> = when (e) {
        is MediaException.UnsupportedFormat -> error(HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE_FORMAT", "Use JPEG, PNG, WebP or GIF.")
        is MediaException.InvalidSize -> error(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE", "An image is at most 10MB.")
        is MediaException.InvalidFileName -> error(HttpStatus.BAD_REQUEST, "INVALID_FILE_NAME", "A file name is 1-255 characters.")
        is MediaException.Attached -> error(HttpStatus.CONFLICT, "IMAGE_IN_USE", "Remove the image from its posts first.")
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, HttpMessageNotReadableException::class)
    fun invalidRequest(): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is not valid.")

    private fun error(status: HttpStatus, code: String, message: String) =
        ResponseEntity.status(status).body(ErrorResponse(code, message))
}

data class ErrorResponse(val code: String, val message: String)
