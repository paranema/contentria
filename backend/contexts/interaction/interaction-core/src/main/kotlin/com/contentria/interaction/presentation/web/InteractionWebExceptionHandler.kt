package com.contentria.interaction.presentation.web

import com.contentria.interaction.application.InteractionException
import com.contentria.interaction.domain.comment.CommentException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
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
        is InteractionException.CommentNotFound -> error(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "No such comment.")
        is InteractionException.InvalidParent -> error(HttpStatus.BAD_REQUEST, "INVALID_PARENT", "The comment replied to is not on this post.")
        is InteractionException.InvalidCursor -> error(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "The page cursor is not valid.")
    }

    @ExceptionHandler
    fun handle(e: CommentException): ResponseEntity<ErrorResponse> = when (e) {
        is CommentException.InvalidBody -> error(HttpStatus.BAD_REQUEST, "INVALID_COMMENT", "A comment is 1-1000 characters.")
        is CommentException.NotAuthor -> error(HttpStatus.FORBIDDEN, "NOT_COMMENT_AUTHOR", "Only the comment's author can change it.")
        is CommentException.ReplyToReply -> error(HttpStatus.BAD_REQUEST, "REPLY_TO_REPLY", "Replies are one level deep.")
        is CommentException.Removed -> error(HttpStatus.CONFLICT, "COMMENT_REMOVED", "The comment was deleted.")
    }

    @ExceptionHandler
    fun handle(e: TooManyIds): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "TOO_MANY_IDS", "Ask for at most $MAX_IDS ids at once.")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, HttpMessageNotReadableException::class)
    fun invalidRequest(): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is not valid.")

    private fun error(status: HttpStatus, code: String, message: String) =
        ResponseEntity.status(status).body(ErrorResponse(code, message))
}

data class ErrorResponse(val code: String, val message: String)
