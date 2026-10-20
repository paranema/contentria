package com.contentria.publishing.adapter.required.member

import com.contentria.member.api.GetAccountStatusQuery
import com.contentria.publishing.application.required.BloggerStatusPort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The translation layer to member: the only place publishing references member
 * (docs/architecture/03-context-integration.md, 예시: 출판이 계정 상태를 확인한다).
 *
 * When member moves to its own service, only this class changes to make a network call. An
 * unanswered call will then be treated as "may not write": a refused write can be retried, a
 * write let through for a suspended blogger cannot be taken back.
 */
@Component
class MemberBloggerStatusAdapter(private val accountStatus: GetAccountStatusQuery) : BloggerStatusPort {

    override fun canPublish(bloggerId: UUID): Boolean = accountStatus.get(bloggerId)?.isActive == true
}
