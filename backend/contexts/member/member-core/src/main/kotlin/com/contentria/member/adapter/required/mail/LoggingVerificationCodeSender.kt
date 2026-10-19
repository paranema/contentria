package com.contentria.member.adapter.required.mail

import com.contentria.member.application.required.CodePurpose
import com.contentria.member.application.required.VerificationCodeSender
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Local development only (`contentria.member.mail.mode=log`). Writes the code to the log
 * instead of sending it, which docs/conventions/logging.md forbids anywhere a real address
 * could be involved. Never enable it in a deployed environment.
 */
@Component
@ConditionalOnProperty(name = ["contentria.member.mail.mode"], havingValue = "log")
class LoggingVerificationCodeSender : VerificationCodeSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(email: String, code: String, purpose: CodePurpose) {
        log.warn("[local mail] {} code {}", purpose, code)
    }
}
