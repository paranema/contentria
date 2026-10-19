package com.contentria.member.adapter.provided.web

import com.contentria.member.application.provided.GetMyCredentialsQuery
import com.contentria.member.application.provided.UnlinkSocialProviderUseCase
import com.contentria.member.domain.account.AuthProvider
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The settings screen's view of how the account signs in. Linking starts with a browser
 * navigation to `/oauth2/authorization/{provider}?intent=link`, not here.
 */
@RestController
@RequestMapping("/me/credentials")
class MyCredentialsController(
    private val credentials: GetMyCredentialsQuery,
    private val unlinkProvider: UnlinkSocialProviderUseCase,
    private val cookies: SessionCookies,
) {

    @GetMapping
    fun get(@AuthenticationPrincipal accountId: UUID): MyCredentialsResponse =
        MyCredentialsResponse(credentials.get(accountId).map { it.name }.sorted())

    /**
     * Social providers only; there is no path that removes the password. Answers with a new
     * session, because unlinking ends every other one.
     */
    @DeleteMapping("/{provider}")
    fun unlink(@AuthenticationPrincipal accountId: UUID, @PathVariable provider: String): ResponseEntity<SessionResponse> {
        val social = AuthProvider.entries.firstOrNull { !it.usesPassword && it.name.equals(provider, ignoreCase = true) }
            ?: return ResponseEntity.notFound().build()
        return cookies.respond(ResponseEntity.ok(), unlinkProvider.unlink(accountId, social))
    }
}
