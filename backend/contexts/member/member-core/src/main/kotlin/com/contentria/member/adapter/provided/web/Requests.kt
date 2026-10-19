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

/** Answer to every request that sends a code: the id to present with the code. */
data class CodeSentResponse(val attemptId: UUID)

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

data class StartCodeLoginRequest(
    @field:NotBlank @field:Size(max = 255) val email: String,
)

data class CompleteCodeLoginRequest(
    @field:NotNull val attemptId: UUID,
    @field:Pattern(regexp = "\\d{6}") val code: String,
)

data class ChangePasswordRequest(
    @field:NotBlank @field:Size(max = 72) val currentPassword: String,
    @field:Size(min = 8, max = 72) val newPassword: String,
)

data class PasswordResetLinkRequest(
    @field:NotBlank @field:Size(max = 255) val email: String,
)

data class ResetPasswordRequest(
    @field:NotBlank @field:Size(max = 100) val token: String,
    @field:Size(min = 8, max = 72) val newPassword: String,
)

data class StartEmailChangeRequest(
    @field:NotBlank @field:Size(max = 255) val newEmail: String,
)

data class CompleteEmailChangeRequest(
    @field:NotNull val attemptId: UUID,
    @field:Pattern(regexp = "\\d{6}") val currentAddressCode: String,
    @field:Pattern(regexp = "\\d{6}") val newAddressCode: String,
)

data class CompleteWithdrawalRequest(
    @field:NotNull val attemptId: UUID,
    @field:Pattern(regexp = "\\d{6}") val code: String,
)

data class RenameProfileRequest(
    @field:NotBlank val displayName: String,
)

/** Token values travel only in cookies; the body says who signed in and until when. */
data class SessionResponse(val accountId: UUID, val accessTokenExpiresAt: Instant)

data class MyCredentialsResponse(val providers: List<String>)

data class ErrorResponse(val code: String, val message: String, val providers: Set<String>? = null)
