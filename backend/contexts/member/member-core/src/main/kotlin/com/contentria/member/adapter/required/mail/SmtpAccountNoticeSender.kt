package com.contentria.member.adapter.required.mail

import com.contentria.member.application.required.AccountNotice
import com.contentria.member.application.required.AccountNoticeSender
import com.contentria.member.config.MemberProperties
import com.contentria.member.domain.account.AuthProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["contentria.member.mail.mode"], havingValue = "smtp", matchIfMissing = true)
class SmtpAccountNoticeSender(
    private val mailSender: JavaMailSender,
    private val properties: MemberProperties,
) : AccountNoticeSender {

    override fun send(email: String, notice: AccountNotice) {
        val (subject, body) = when (notice) {
            is AccountNotice.SocialProviderLinked ->
                "[Contentria] ${label(notice.provider)} 계정이 연결되었습니다" to
                    (if (notice.automatic) "같은 이메일 주소의 ${label(notice.provider)} 계정으로 로그인해 자동으로 연결했습니다." else "설정 화면에서 ${label(notice.provider)} 계정을 연결했습니다.")
            is AccountNotice.SocialProviderUnlinked ->
                "[Contentria] ${label(notice.provider)} 계정 연결이 해제되었습니다" to
                    "${label(notice.provider)} 계정으로는 더 이상 로그인할 수 없습니다."
            AccountNotice.PasswordChanged ->
                "[Contentria] 비밀번호가 변경되었습니다" to "다른 기기의 로그인은 모두 끝났습니다."
            AccountNotice.EmailChanged ->
                "[Contentria] 계정 이메일이 변경되었습니다" to "이 주소는 더 이상 계정에 연결되어 있지 않습니다. 다른 기기의 로그인은 모두 끝났습니다."
        }
        val message = SimpleMailMessage().apply {
            from = properties.mail.from
            setTo(email)
            this.subject = subject
            text = "$body\n\n$UNREQUESTED_NOTICE"
        }
        mailSender.send(message)
    }

    private fun label(provider: AuthProvider): String = when (provider) {
        AuthProvider.GOOGLE -> "Google"
        AuthProvider.EMAIL -> "이메일"
    }

    private companion object {
        const val UNREQUESTED_NOTICE = "직접 한 일이 아니라면 비밀번호를 바꾸고 연결된 계정을 확인하세요."
    }
}
