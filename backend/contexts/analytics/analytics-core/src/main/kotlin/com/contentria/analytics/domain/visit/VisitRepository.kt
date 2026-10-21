package com.contentria.analytics.domain.visit

import org.springframework.data.repository.Repository
import java.time.Instant
import java.util.UUID

// Spring Data marker repository, in the domain as the layered structure has it
// (docs/architecture/02-internal-architecture.md, 리포지토리). Only the methods declared here exist.
interface VisitRepository : Repository<Visit, UUID> {
    fun save(visit: Visit): Visit

    /** 01-rules 흐름 1의 4번, for a post and for the blog's home. Two queries: a null post id is "the home", not "any". */
    fun existsByBlogIdAndPostIdAndVisitorKeyAndVisitedAtAfter(blogId: UUID, postId: UUID, visitorKey: String, after: Instant): Boolean
    fun existsByBlogIdAndPostIdIsNullAndVisitorKeyAndVisitedAtAfter(blogId: UUID, visitorKey: String, after: Instant): Boolean
}
