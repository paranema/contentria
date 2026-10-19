package com.contentria.member.application.provided

import java.time.Instant
import java.util.UUID

interface RefreshSessionUseCase {
    /** Rotates the refresh token: the presented one stops working, a new pair is returned. */
    fun refresh(refreshToken: String): IssuedSession
}

interface LogoutUseCase {
    /**
     * Ends the session [refreshToken] belongs to, and only that one. A missing, unknown or already
     * rotated token is not an error: the caller is signed out either way.
     */
    fun logout(refreshToken: String?)
}

data class IssuedSession(
    val accountId: UUID,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)
