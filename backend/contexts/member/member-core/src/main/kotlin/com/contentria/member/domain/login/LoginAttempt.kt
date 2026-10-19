package com.contentria.member.domain.login

import com.contentria.member.domain.MemberSchema
import com.contentria.member.domain.verification.EmailCodeAttempt
import com.contentria.shared.id.Ids
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A code login that has sent a code to an account's address (docs/contexts/member/01-rules.md,
 * 흐름 6).
 *
 * Holds the address, not the account id. The account is looked up by the address again when
 * the code comes back, so an account whose address has changed or been anonymised in the
 * meantime is not reached through a code sent to its old one.
 */
@Entity
@Table(name = "login_attempts", schema = MemberSchema.NAME)
class LoginAttempt private constructor(id: UUID, email: String, code: String, now: Instant) :
    EmailCodeAttempt(id, email, code, now) {

    override fun equals(other: Any?): Boolean =
        this === other || (other is LoginAttempt && id == other.id)

    companion object {
        fun start(email: String, code: String, now: Instant) = LoginAttempt(Ids.newId(), email, code, now)
    }
}
