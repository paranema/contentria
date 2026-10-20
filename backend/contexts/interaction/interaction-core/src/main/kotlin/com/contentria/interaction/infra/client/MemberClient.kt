package com.contentria.interaction.infra.client

import com.contentria.member.api.GetAccountStatusQuery
import org.springframework.stereotype.Component
import java.util.UUID

/** The translation layer to member. An account member does not know may not interact (01-rules 전제 4). */
@Component
class MemberClient(private val accountStatus: GetAccountStatusQuery) {

    fun canInteract(accountId: UUID): Boolean = accountStatus.get(accountId)?.isActive == true
}
