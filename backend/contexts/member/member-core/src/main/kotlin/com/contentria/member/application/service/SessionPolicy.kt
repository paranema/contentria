package com.contentria.member.application.service

import java.time.Duration

/** How long a refresh token lives. The access token's lifetime belongs to its codec. */
data class SessionPolicy(val refreshTokenTtl: Duration)
