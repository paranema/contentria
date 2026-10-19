package com.contentria.member.adapter.required.mail

import com.contentria.member.application.required.VerificationCodeSender
import com.contentria.member.config.MemberProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["contentria.member.mail.mode"], havingValue = "smtp", matchIfMissing = true)
class SmtpVerificationCodeSender(
    private val mailSender: JavaMailSender,
    private val properties: MemberProperties,
) : VerificationCodeSender {

    override fun send(email: String, code: String) {
        val message = SimpleMailMessage().apply {
            from = properties.mail.from
            setTo(email)
            subject = "[Contentria] 인증 코드"
            text = "인증 코드: $code\n\n${properties.mail.codeValidityNotice}"
        }
        mailSender.send(message)
    }
}
