package com.contentria.app.api

import com.contentria.member.application.required.AccountNotice
import com.contentria.member.application.required.AccountNoticeSender
import com.contentria.member.application.required.CodePurpose
import com.contentria.member.application.required.HumanVerifier
import com.contentria.member.application.required.InvisibleAssessment
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

/**
 * Answers in place of Google's reCAPTCHA. A request carrying [HUMAN] passes either check; [BOT]
 * scores too low on the invisible one. Any other token is one Google would not accept.
 */
class FakeHumanVerifier : HumanVerifier {
    override fun assessInvisible(token: String): InvisibleAssessment? = when (token) {
        HUMAN -> InvisibleAssessment(0.9, null)
        BOT -> InvisibleAssessment(0.1, null)
        else -> null
    }

    override fun acceptsCheckbox(token: String): Boolean = token == HUMAN

    companion object {
        const val HUMAN = "human"
        const val BOT = "bot"
    }
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

    @Bean
    @Primary
    fun fakeHumanVerifier() = FakeHumanVerifier()
}
