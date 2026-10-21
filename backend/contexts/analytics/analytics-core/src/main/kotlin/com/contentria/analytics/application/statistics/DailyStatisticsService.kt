package com.contentria.analytics.application.statistics

import com.contentria.analytics.infra.persistence.DailyStatisticsSql
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * docs/contexts/analytics/01-rules.md, 흐름 2. The last [DAYS_AGAIN] finished days are counted again
 * every run, each in its own transaction, so a run that was missed is made up by the next one.
 */
@Service
class DailyStatisticsService(
    private val statistics: DailyStatisticsSql,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {

    fun aggregateRecentDays(): DailyStatisticsReport {
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, SEOUL)
        val rows = (1..DAYS_AGAIN).sumOf { back ->
            val day = today.minusDays(back.toLong())
            transactions.execute {
                statistics.aggregate(day, day.atStartOfDay(SEOUL).toInstant(), day.plusDays(1).atStartOfDay(SEOUL).toInstant(), now)
            }!!
        }
        val removed = transactions.execute { statistics.deleteVisitsBefore(now.minus(VISIT_RETENTION)) }!!
        return DailyStatisticsReport(rows, removed)
    }

    companion object {
        /** 01-rules 흐름 2의 3번 */
        const val DAYS_AGAIN = 3

        /** 01-rules, 처음 정하는 값 */
        val VISIT_RETENTION: Duration = Duration.ofDays(90)

        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

/** [rows] daily statistics written, [removedVisits] visits past their 90 days. */
data class DailyStatisticsReport(val rows: Int, val removedVisits: Int)
