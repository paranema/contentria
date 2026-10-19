package com.contentria.app.api

import com.contentria.member.application.required.AccountNotice
import com.contentria.member.application.required.AccountNoticeSender
import com.contentria.member.application.required.CodePurpose
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

@TestConfiguration
class RecordingSenders {
    @Bean
    @Primary
    fun recordingCodeSender() = RecordingCodeSender()

    @Bean
    @Primary
    fun recordingNoticeSender() = RecordingNoticeSender()
}
