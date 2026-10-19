package com.contentria.member.adapter.required.mail

import com.contentria.member.application.required.AccountNotice
import com.contentria.member.application.required.AccountNoticeSender
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** Local development only (`contentria.member.mail.mode=log`). Logs the notice, never the address. */
@Component
@ConditionalOnProperty(name = ["contentria.member.mail.mode"], havingValue = "log")
class LoggingAccountNoticeSender : AccountNoticeSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(email: String, notice: AccountNotice) {
        log.info("[local mail] account notice {}", notice)
    }
}
