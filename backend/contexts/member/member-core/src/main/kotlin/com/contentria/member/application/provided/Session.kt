package com.contentria.member.application.provided

import java.time.Instant
import java.util.UUID

interface RefreshSessionUseCase {
    /** Rotates the refresh token: the presented one stops working, a new pair is returned. */
    fun refresh(refreshToken: String): IssuedSession
}

data class IssuedSession(
    val accountId: UUID,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)
