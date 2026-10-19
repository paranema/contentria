package com.contentria.member.domain

import java.security.MessageDigest

/** Hex SHA-256, for values stored only so they can be compared later (codes, refresh tokens). */
internal object Sha256 {
    fun hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
