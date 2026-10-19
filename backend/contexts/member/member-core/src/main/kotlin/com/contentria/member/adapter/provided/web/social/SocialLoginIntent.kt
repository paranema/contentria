package com.contentria.member.adapter.provided.web.social

import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import java.util.UUID

/**
 * Why the browser went to the provider. Sign-in and manual linking come back to the same
 * callback (01-rules 흐름 3), so the intent travels inside the signed authorization request.
 */
enum class SocialLoginIntent {
    LOGIN,
    LINK;

    companion object {
        const val PARAMETER = "intent"
        internal const val ATTRIBUTE = "contentria.intent"
        internal const val LINKING_ACCOUNT_ATTRIBUTE = "contentria.linking-account"

        fun of(request: OAuth2AuthorizationRequest?): SocialLoginIntent =
            request?.getAttribute<String>(ATTRIBUTE)?.let(::valueOf) ?: LOGIN

        /** The account that was signed in when a link started; null if nobody was. */
        fun linkingAccountOf(request: OAuth2AuthorizationRequest?): UUID? =
            request?.getAttribute<String>(LINKING_ACCOUNT_ATTRIBUTE)?.let(UUID::fromString)
    }
}
