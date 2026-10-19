package com.contentria.app.api

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stands in for Google's token and key endpoints, signing real RS256 ID tokens.
 *
 * The consent screen is skipped: a test reads the redirect to `/authorize`, decides who the
 * user is with [issueCode], and sends the browser to the callback itself.
 */
class FakeOidcProvider {

    /** What the provider says about the user. A null [emailVerified] leaves the claim out. */
    data class Identity(val subject: String, val email: String?, val emailVerified: Boolean?)

    private class Grant(val identity: Identity, val nonce: String, val forged: Boolean)

    private val key = RSAKeyGenerator(2048).keyID("test-key").generate()

    /** Same key id, different key: what an attacker without Google's private key could produce. */
    private val forgeryKey = RSAKeyGenerator(2048).keyID("test-key").generate()
    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
    private val grants = ConcurrentHashMap<String, Grant>()

    /** Form fields of every token request, so a test can check what the client sent. */
    val tokenRequests: MutableList<Map<String, String>> = CopyOnWriteArrayList()

    fun start() {
        server.createContext("/token") { exchange ->
            val form = parseForm(exchange.requestBody.readAllBytes().decodeToString())
            tokenRequests += form
            val grant = form["code"]?.let(grants::remove)
            if (grant == null) {
                respond(exchange, 400, """{"error":"invalid_grant"}""")
            } else {
                respond(exchange, 200, """{"access_token":"${UUID.randomUUID()}","token_type":"Bearer","expires_in":300,"scope":"openid email","id_token":"${idToken(grant)}"}""")
            }
        }
        server.createContext("/jwks") { exchange -> respond(exchange, 200, JWKSet(key.toPublicJWK()).toString()) }
        server.start()
    }

    fun url(path: String): String = "http://localhost:${server.address.port}$path"

    /**
     * The code the user would bring back from the consent screen. [nonce] is the one the client
     * sent. A [forged] grant's ID token is signed with a key that is not in the published set.
     */
    fun issueCode(identity: Identity, nonce: String, forged: Boolean = false): String =
        UUID.randomUUID().toString().also { grants[it] = Grant(identity, nonce, forged) }

    private fun idToken(grant: Grant): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(grant.identity.subject)
            .audience(CLIENT_ID)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("nonce", grant.nonce)
            .apply {
                grant.identity.email?.let { claim("email", it) }
                grant.identity.emailVerified?.let { claim("email_verified", it) }
            }
            .build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
            .apply { sign(RSASSASigner(if (grant.forged) forgeryKey else key)) }
            .serialize()
    }

    private fun respond(exchange: HttpExchange, status: Int, json: String) {
        val body = json.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun parseForm(body: String): Map<String, String> =
        body.split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
        }

    companion object {
        const val CLIENT_ID = "contentria-test-client"

        /** Boot's Google defaults expect this issuer. */
        const val ISSUER = "https://accounts.google.com"
    }
}
