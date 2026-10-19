package com.contentria.member.domain.emailchange

import com.contentria.member.domain.MemberSchema
import com.contentria.member.domain.verification.CodeVerification
import com.contentria.member.domain.verification.EmailCodeAttempt
import com.contentria.shared.id.Ids
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A request to move an account to a new address, waiting for two codes: one sent to the current
 * address, which re-confirms the owner, and one sent to the new address, which proves it
 * (01-rules 흐름 4, 이메일 변경). The account keeps its address until both come back.
 *
 * [email] is the new address. The two codes are checked as one value, so a wrong pair costs one
 * failed attempt and the five-attempt limit applies to the pair.
 */
@Entity
@Table(name = "email_change_attempts", schema = MemberSchema.NAME)
class EmailChangeAttempt private constructor(
    id: UUID,
    accountId: UUID,
    newEmail: String,
    currentAddressCode: String,
    newAddressCode: String,
    now: Instant,
) : EmailCodeAttempt(id, newEmail, currentAddressCode + newAddressCode, now) {

    @Column(name = "account_id", nullable = false, updatable = false)
    val accountId: UUID = accountId

    fun verify(currentAddressCode: String, newAddressCode: String, now: Instant): CodeVerification =
        verify(currentAddressCode + newAddressCode, now)

    override fun equals(other: Any?): Boolean =
        this === other || (other is EmailChangeAttempt && id == other.id)

    companion object {
        fun start(accountId: UUID, newEmail: String, currentAddressCode: String, newAddressCode: String, now: Instant) =
            EmailChangeAttempt(Ids.newId(), accountId, newEmail, currentAddressCode, newAddressCode, now)
    }
}
