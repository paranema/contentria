package com.contentria.member.adapter.required.mail

import com.contentria.member.application.required.PasswordLinkPurpose
import com.contentria.member.application.required.PasswordLinkSender
import com.contentria.member.config.MemberProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * The token goes in the fragment (`#token=`). A browser never sends the fragment to a server, so
 * the token stays out of access logs and Referer headers; the frontend reads it and posts it.
 */
@Component
@ConditionalOnProperty(name = ["contentria.member.mail.mode"], havingValue = "smtp", matchIfMissing = true)
class SmtpPasswordLinkSender(
    private val mailSender: JavaMailSender,
    private val properties: MemberProperties,
) : PasswordLinkSender {

    override fun send(email: String, token: String, purpose: PasswordLinkPurpose) {
        val (subject, intro) = when (purpose) {
            PasswordLinkPurpose.SETUP -> "[Contentria] 비밀번호 설정 링크" to "아래 링크에서 비밀번호를 설정하세요."
            PasswordLinkPurpose.RESET -> "[Contentria] 비밀번호 재설정 링크" to "아래 링크에서 새 비밀번호를 정하세요."
        }
        val message = SimpleMailMessage().apply {
            from = properties.mail.from
            setTo(email)
            this.subject = subject
            text = "$intro\n\n${properties.password.linkUrl}#token=$token\n\n" +
                "링크는 30분 동안 한 번만 쓸 수 있습니다.\n직접 요청하지 않았다면 이 메일을 무시하세요."
        }
        mailSender.send(message)
    }
}
