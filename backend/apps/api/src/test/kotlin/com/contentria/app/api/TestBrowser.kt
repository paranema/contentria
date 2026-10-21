package com.contentria.app.api

import java.net.CookieManager
import java.net.HttpCookie
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** A client that keeps cookies between requests and does not follow redirects, like one browser tab. */
class TestBrowser(private val port: Int) {

    private val cookies = CookieManager()
    private val client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build()

    fun get(path: String) = send(request(path).GET())
    fun getAbsolute(url: String) = send(HttpRequest.newBuilder(URI.create(url)).GET())
    fun post(path: String, json: String) = send(request(path).POST(HttpRequest.BodyPublishers.ofString(json)))
    fun patch(path: String, json: String) = send(request(path).method("PATCH", HttpRequest.BodyPublishers.ofString(json)))
    fun delete(path: String) = send(request(path).DELETE())
    fun send(method: String, path: String, json: String) = send(request(path).method(method, HttpRequest.BodyPublishers.ofString(json)))
    fun post(path: String, json: String, headers: Map<String, String>) =
        send(request(path).apply { headers.forEach { (name, value) -> header(name, value) } }.POST(HttpRequest.BodyPublishers.ofString(json)))
    fun postWithCookie(path: String, cookie: String) =
        send(request(path).header("Cookie", cookie).POST(HttpRequest.BodyPublishers.noBody()))

    fun cookie(name: String): String? = cookies.cookieStore.cookies.firstOrNull { it.name == name }?.value

    /** Replaces a cookie's value, as a visitor editing their own browser could. */
    fun rewriteCookie(name: String, transform: (String) -> String) {
        val uri = URI.create("http://localhost:$port/")
        val existing = cookies.cookieStore.cookies.single { it.name == name }
        cookies.cookieStore.remove(uri, existing)
        // Version 0: a version 1 cookie goes out quoted in RFC 2965 form, which the server would not read.
        cookies.cookieStore.add(uri, HttpCookie(name, transform(existing.value)).apply { path = "/"; version = 0 })
    }

    private fun request(path: String) =
        HttpRequest.newBuilder(URI.create("http://localhost:$port/api$path")).header("Content-Type", "application/json")

    private fun send(builder: HttpRequest.Builder): HttpResponse<String> =
        client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
}

fun HttpResponse<String>.location(): String = headers().firstValue("Location").orElseThrow()

fun HttpResponse<String>.field(name: String): String =
    Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(body())?.groupValues?.get(1)
        ?: error("No $name in ${body()}")
