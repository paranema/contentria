package com.contentria.member.domain.signup

import com.contentria.member.domain.MemberSchema
import com.contentria.member.domain.verification.EmailCodeAttempt
import com.contentria.shared.id.Ids
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * An email sign-up that has sent a code and is waiting for it to come back.
 *
 * Not part of the Account aggregate: there is no account yet, and nothing here is an account
 * invariant (docs/contexts/member/02-implementation.md, 애그리거트 경계). It holds only the
 * address and the code, never a password or any other input, so that two attempts for the same
 * address differ in nothing but the code (01-rules, 가입 시도에 사용자 입력을 담지 않는다).
 */
@Entity
@Table(name = "signup_attempts", schema = MemberSchema.NAME)
class SignUpAttempt private constructor(id: UUID, email: String, code: String, now: Instant) :
    EmailCodeAttempt(id, email, code, now) {

    override fun equals(other: Any?): Boolean =
        this === other || (other is SignUpAttempt && id == other.id)

    companion object {
        fun start(email: String, code: String, now: Instant) = SignUpAttempt(Ids.newId(), email, code, now)
    }
}
