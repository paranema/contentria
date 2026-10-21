package com.contentria.analytics.application.visit

import java.time.LocalDate
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Who a reader is, for one day only (docs/contexts/analytics/01-rules.md, IP를 남기지 않는다):
 * HMAC-SHA256 over the date, the IP and the User-Agent. Without the secret the IP cannot be
 * recovered; with another date the same reader gets another key.
 */
class VisitorKeys(secret: String) {

    private val key = SecretKeySpec(secret.toByteArray(), ALGORITHM)

    init {
        require(secret.length >= MIN_SECRET_LENGTH) { "The visitor key secret is at least $MIN_SECRET_LENGTH characters" }
    }

    fun of(day: LocalDate, ip: String, userAgent: String?): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(key) }
        // Separators that cannot occur inside the parts, so "a|b" + "c" never equals "a" + "b|c".
        val input = "$day\u0000$ip\u0000${userAgent.orEmpty()}"
        return mac.doFinal(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        const val MIN_SECRET_LENGTH = 32
    }
}
