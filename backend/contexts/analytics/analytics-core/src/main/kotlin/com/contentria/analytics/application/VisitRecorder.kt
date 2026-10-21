package com.contentria.analytics.application

import com.contentria.analytics.domain.visit.Visit
import com.contentria.analytics.domain.visit.VisitRepository
import com.contentria.analytics.infra.client.PublishingClient
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * docs/contexts/analytics/01-rules.md, 흐름 1. Publishing is asked before the transaction opens.
 * Every refusal is silent: the reader's page has nothing to do about it.
 */
@Service
class VisitRecorder(
    private val visits: VisitRepository,
    private val publishing: PublishingClient,
    private val keys: VisitorKeys,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {

    /** Returns whether the visit was counted. */
    fun record(command: RecordVisitCommand): Boolean {
        // 1번
        val blog = publishing.blog(command.blogId) ?: return false
        // 2번
        if (command.postId != null && publishing.publicPost(command.postId)?.blogId != blog.blogId) return false
        // 3번
        if (command.viewerId == blog.ownerId) return false

        val now = clock.instant()
        val visitorKey = keys.of(LocalDate.ofInstant(now, SEOUL), command.ip, command.userAgent)
        return transactions.execute {
            // 4번. Two visits at the same moment may both pass; the count is an estimate, not a ledger.
            val since = now.minus(DEDUP_WINDOW)
            val seen = if (command.postId == null) {
                visits.existsByBlogIdAndPostIdIsNullAndVisitorKeyAndVisitedAtAfter(blog.blogId, visitorKey, since)
            } else {
                visits.existsByBlogIdAndPostIdAndVisitorKeyAndVisitedAtAfter(blog.blogId, command.postId, visitorKey, since)
            }
            // 5번
            if (!seen) visits.save(Visit.record(blog.blogId, command.postId, visitorKey, command.referrer, now))
            !seen
        }!!
    }

    companion object {
        /** 01-rules, 처음 정하는 값 */
        val DEDUP_WINDOW: Duration = Duration.ofMinutes(30)

        /** 01-rules, 처음 정하는 값: a day is a Korean day. */
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

/** [viewerId] is the signed-in reader, or null. [ip] and [userAgent] never leave this command. */
data class RecordVisitCommand(
    val blogId: UUID,
    val postId: UUID?,
    val viewerId: UUID?,
    val ip: String,
    val userAgent: String?,
    val referrer: String?,
)
