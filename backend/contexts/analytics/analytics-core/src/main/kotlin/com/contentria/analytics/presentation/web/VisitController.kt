package com.contentria.analytics.presentation.web

import com.contentria.analytics.application.RecordVisitCommand
import com.contentria.analytics.application.VisitRecorder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

/**
 * docs/contexts/analytics/01-rules.md, 흐름 1. Open to readers who are not signed in; the host's
 * filter chain allows `POST /analytics/visits`. The IP is the request's remote address: behind a
 * proxy the host corrects it from the forwarding headers, and this class never reads them.
 */
@RestController
class VisitController(private val recorder: VisitRecorder) {

    @PostMapping("/analytics/visits")
    fun record(
        @AuthenticationPrincipal viewerId: UUID?,
        @RequestBody request: VisitRequest,
        http: HttpServletRequest,
    ): ResponseEntity<Void> {
        recorder.record(
            RecordVisitCommand(
                blogId = request.blogId,
                postId = request.postId,
                viewerId = viewerId,
                ip = http.remoteAddr,
                userAgent = http.getHeader(HttpHeaders.USER_AGENT),
                referrer = request.referrer,
            ),
        )
        // Counted or not, the page is told the same.
        return ResponseEntity.noContent().build()
    }
}

/** [referrer] is the page the reader came from (document.referrer); only its host is kept. */
data class VisitRequest(val blogId: UUID, val postId: UUID? = null, val referrer: String? = null)

@RestControllerAdvice(basePackageClasses = [VisitController::class])
class AnalyticsWebExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun invalidRequest(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("INVALID_REQUEST", "The request is not valid."))
}

data class ErrorResponse(val code: String, val message: String)
