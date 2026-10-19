package com.contentria.app.api

import com.contentria.member.application.required.AccountNotice
import com.contentria.member.application.required.AccountNoticeSender
import com.contentria.member.application.required.CodePurpose
import com.contentria.member.application.required.PasswordLinkPurpose
import com.contentria.member.application.required.PasswordLinkSender
import com.contentria.member.application.required.VerificationCodeSender
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Captures codes instead of mailing them. */
class RecordingCodeSender : VerificationCodeSender {
    private val codes = ConcurrentHashMap<Pair<String, CodePurpose>, String>()
    override fun send(email: String, code: String, purpose: CodePurpose) { codes[email to purpose] = code }
    fun lastCodeFor(email: String, purpose: CodePurpose = CodePurpose.SIGN_UP): String =
        codes[email to purpose] ?: error("No $purpose code was sent to $email")
    fun sentAnyTo(email: String): Boolean = codes.keys.any { it.first == email }
}

/** Captures account notices instead of mailing them. */
class RecordingNoticeSender : AccountNoticeSender {
    private val notices = CopyOnWriteArrayList<Pair<String, AccountNotice>>()
    override fun send(email: String, notice: AccountNotice) { notices += email to notice }
    fun sentTo(email: String): List<AccountNotice> = notices.filter { it.first == email }.map { it.second }
}

/** Captures password link tokens instead of mailing them. */
class RecordingPasswordLinkSender : PasswordLinkSender {
    private val tokens = ConcurrentHashMap<Pair<String, PasswordLinkPurpose>, String>()
    override fun send(email: String, token: String, purpose: PasswordLinkPurpose) { tokens[email to purpose] = token }
    fun lastTokenFor(email: String, purpose: PasswordLinkPurpose): String =
        tokens[email to purpose] ?: error("No $purpose link was sent to $email")
    fun sentAnyTo(email: String): Boolean = tokens.keys.any { it.first == email }
}

@TestConfiguration
class RecordingSenders {
    @Bean
    @Primary
    fun recordingCodeSender() = RecordingCodeSender()

    @Bean
    @Primary
    fun recordingNoticeSender() = RecordingNoticeSender()

    @Bean
    @Primary
    fun recordingPasswordLinkSender() = RecordingPasswordLinkSender()
}
