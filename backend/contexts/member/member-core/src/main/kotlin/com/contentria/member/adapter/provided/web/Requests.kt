package com.contentria.member.adapter.provided.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class StartSignUpRequest(
    @field:NotBlank @field:Size(max = 255) val email: String,
)

data class StartSignUpResponse(val attemptId: UUID)

data class CompleteSignUpRequest(
    @field:NotNull val attemptId: UUID,
    @field:Pattern(regexp = "\\d{6}") val code: String,
    // BCrypt reads at most 72 bytes; a longer password would be silently truncated.
    @field:Size(min = 8, max = 72) val password: String,
)

data class LoginRequest(
    @field:NotBlank @field:Size(max = 255) val email: String,
    @field:NotBlank @field:Size(max = 72) val password: String,
)

data class RenameProfileRequest(
    @field:NotBlank val displayName: String,
)

/** Token values travel only in cookies; the body says who signed in and until when. */
data class SessionResponse(val accountId: UUID, val accessTokenExpiresAt: Instant)

data class ErrorResponse(val code: String, val message: String, val providers: Set<String>? = null)
