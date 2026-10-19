package com.contentria.member.adapter.required.random

import com.contentria.member.application.required.VerificationCodeGenerator
import org.springframework.stereotype.Component
import java.security.SecureRandom

/** Six digits from a cryptographically secure source (01-rules, 가입 시도: 코드 생성). */
@Component
class SecureRandomVerificationCodeGenerator : VerificationCodeGenerator {

    private val random = SecureRandom()

    override fun generate(): String = "%06d".format(random.nextInt(1_000_000))
}
