package com.contentria.analytics.presentation.batch

import com.contentria.analytics.application.statistics.DailyStatisticsService
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * docs/contexts/analytics/01-rules.md, 흐름 2, as a one-shot job that apps/batch runs once a day.
 * The step is not transactional: every day counted has its own transaction in the use case.
 */
@Configuration(proxyBeanMethods = false)
class DailyStatisticsJob {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun analyticsDailyStatisticsJob(jobRepository: JobRepository, statistics: DailyStatisticsService): Job {
        val step = StepBuilder(STEP, jobRepository)
            .tasklet({ contribution, _ ->
                val report = statistics.aggregateRecentDays()
                contribution.incrementWriteCount(report.rows.toLong())
                log.info("Daily statistics: {} rows written, {} old visits removed", report.rows, report.removedVisits)
                RepeatStatus.FINISHED
            }, ResourcelessTransactionManager())
            .build()
        return JobBuilder(NAME, jobRepository).start(step).build()
    }

    companion object {
        const val NAME = "analyticsDailyStatistics"
        private const val STEP = "analyticsDailyStatisticsStep"
    }
}
