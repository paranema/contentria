package com.contentria.member.adapter.required.mail

import com.contentria.member.application.required.PasswordLinkPurpose
import com.contentria.member.application.required.PasswordLinkSender
import com.contentria.member.config.MemberProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** Local development only (`contentria.member.mail.mode=log`), like the logging code sender. */
@Component
@ConditionalOnProperty(name = ["contentria.member.mail.mode"], havingValue = "log")
class LoggingPasswordLinkSender(private val properties: MemberProperties) : PasswordLinkSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(email: String, token: String, purpose: PasswordLinkPurpose) {
        log.warn("[local mail] {} link {}#token={}", purpose, properties.password.linkUrl, token)
    }
}
