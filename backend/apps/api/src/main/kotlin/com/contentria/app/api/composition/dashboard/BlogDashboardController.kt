package com.contentria.app.api.composition.dashboard

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

/** The blog's owner's dashboard: one request for the summary, the trend and the popular posts. */
@RestController
class BlogDashboardController(private val dashboard: BlogDashboard) {

    @GetMapping("/me/blogs/{slug}/dashboard")
    fun dashboard(
        @AuthenticationPrincipal accountId: UUID,
        @PathVariable slug: String,
        @RequestParam(defaultValue = "2weeks") range: String,
    ): DashboardView = dashboard.of(accountId, slug, DashboardRange.of(range))
}

@RestControllerAdvice(basePackageClasses = [BlogDashboardController::class])
class DashboardExceptionHandler {

    @ExceptionHandler(DashboardBlogNotFound::class)
    fun notFound(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("BLOG_NOT_FOUND", "No blog has this address."))

    @ExceptionHandler(InvalidDashboardRange::class)
    fun invalidRange(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("INVALID_REQUEST", "The range is 2weeks, 30days or 90days."))
}

data class ErrorResponse(val code: String, val message: String)
