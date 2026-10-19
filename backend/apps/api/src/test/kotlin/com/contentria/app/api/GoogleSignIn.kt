package com.contentria.app.api

import com.contentria.app.api.FakeOidcProvider.Identity
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpResponse

/**
 * Plays the browser's part of a Google sign-in: follows the API's redirect to the provider, has
 * the fake provider issue a code for [identity], and returns with it to the callback.
 */
fun TestBrowser.signInWithGoogle(
    identity: Identity,
    intent: String? = null,
    forged: Boolean = false,
    beforeCallback: () -> Unit = {},
): HttpResponse<String> {
    val google = ApiTestEnvironment.google
    val start = get("/oauth2/authorization/google" + (intent?.let { "?intent=$it" } ?: ""))
    check(start.statusCode() == 302) { "Expected a redirect to the provider, got ${start.statusCode()}" }

    val authorize = URI.create(start.location())
    check(authorize.toString().startsWith(google.url("/authorize"))) { "Redirected to $authorize" }
    val query = authorize.rawQuery.split('&').associate {
        it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
    }
    check(query["code_challenge_method"] == "S256") { "No PKCE in $authorize" }

    val code = google.issueCode(identity, nonce = query.getValue("nonce"), forged = forged)
    beforeCallback()
    val state = URLEncoder.encode(query.getValue("state"), Charsets.UTF_8)
    return getAbsolute("${query.getValue("redirect_uri")}?code=$code&state=$state")
}
