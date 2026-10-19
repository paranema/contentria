package com.contentria.member.adapter.required.mail

import com.contentria.member.application.required.CodePurpose
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

    override fun send(email: String, code: String, purpose: CodePurpose) {
        val message = SimpleMailMessage().apply {
            from = properties.mail.from
            setTo(email)
            subject = when (purpose) {
                CodePurpose.SIGN_UP -> "[Contentria] 회원가입 인증 코드"
                CodePurpose.LOGIN -> "[Contentria] 로그인 코드"
            }
            text = "인증 코드: $code\n\n${properties.mail.codeValidityNotice}\n$UNREQUESTED_NOTICE"
        }
        mailSender.send(message)
    }

    private companion object {
        const val UNREQUESTED_NOTICE = "직접 요청하지 않았다면 이 메일을 무시하고, 코드를 누구에게도 알려주지 마세요."
    }
}
