package com.contentria.member.application.service

import com.contentria.member.application.required.AccountNotice
import com.contentria.member.application.required.AccountNoticeSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** A notice for an account's owner, to send once the change that prompted it has committed. */
data class AccountNoticeRequested(val email: String, val notice: AccountNotice)

/**
 * Sends [AccountNoticeRequested] after commit (docs/contexts/member/02-implementation.md, 이벤트).
 *
 * In-process: a crash between the commit and the send loses the notice. That is accepted until
 * the context has an outbox. A failed send is logged and swallowed, since the change it reports
 * has already happened.
 */
@Component
class AccountNoticeListener(private val sender: AccountNoticeSender) {

    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: AccountNoticeRequested) {
        try {
            sender.send(event.email, event.notice)
        } catch (e: RuntimeException) {
            // The exception's message may carry the address (docs/conventions/logging.md).
            log.warn("Account notice {} was not sent: {}", event.notice::class.simpleName, e.javaClass.name)
        }
    }
}
