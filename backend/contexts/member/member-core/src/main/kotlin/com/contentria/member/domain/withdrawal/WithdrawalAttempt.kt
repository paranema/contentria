package com.contentria.member.domain.withdrawal

import com.contentria.member.domain.MemberSchema
import com.contentria.member.domain.verification.EmailCodeAttempt
import com.contentria.shared.id.Ids
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A withdrawal waiting for a code sent to the account's address. A signed-in session alone does
 * not close an account: the step cannot be undone, so the owner of the address confirms it.
 */
@Entity
@Table(name = "withdrawal_attempts", schema = MemberSchema.NAME)
class WithdrawalAttempt private constructor(id: UUID, accountId: UUID, email: String, code: String, now: Instant) :
    EmailCodeAttempt(id, email, code, now) {

    @Column(name = "account_id", nullable = false, updatable = false)
    val accountId: UUID = accountId

    override fun equals(other: Any?): Boolean =
        this === other || (other is WithdrawalAttempt && id == other.id)

    companion object {
        fun start(accountId: UUID, email: String, code: String, now: Instant) =
            WithdrawalAttempt(Ids.newId(), accountId, email, code, now)
    }
}
