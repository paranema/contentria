package com.contentria.member.adapter.required.captcha

import com.contentria.member.application.required.HumanVerifier
import com.contentria.member.application.required.HumanVerifierUnavailable
import com.contentria.member.application.required.InvisibleAssessment
import com.contentria.member.config.MemberProperties
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Google reCAPTCHA's siteverify: v3 for the invisible check, v2 for the checkbox, each with its
 * own secret. Errors carry no token or secret.
 */
@Component
class RecaptchaVerifier(properties: MemberProperties) : HumanVerifier {

    private val settings = properties.recaptcha
    private val http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
    private val json = JsonMapper.builder().build()

    override fun assessInvisible(token: String): InvisibleAssessment? {
        val answer = verify(settings.v3SecretKey, token)
        if (!answer.path("success").asBoolean(false)) return null
        val score = answer.path("score").takeIf { it.isNumber }?.asDouble() ?: return null
        return InvisibleAssessment(score, answer.path("action").takeIf { it.isString }?.asString())
    }

    override fun acceptsCheckbox(token: String): Boolean = verify(settings.v2SecretKey, token).path("success").asBoolean(false)

    private fun verify(secret: String, token: String) = try {
        val form = "secret=${encode(secret)}&response=${encode(token)}"
        val request = HttpRequest.newBuilder(settings.verifyUrl)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw HumanVerifierUnavailable()
        json.readTree(response.body())
    } catch (e: IOException) {
        throw HumanVerifierUnavailable(e)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw HumanVerifierUnavailable(e)
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
