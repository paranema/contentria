package com.contentria.media.adapter.provided.batch

import com.contentria.media.application.provided.CleanUpVideosUseCase
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
 * docs/contexts/media/01-rules.md, 흐름 V5, as a one-shot job that apps/batch runs on a schedule.
 * As [ImageCleanupJob]: every video has its own transaction in the use case, and any video that
 * could not be removed fails the job so that the schedule's run shows it.
 */
@Configuration(proxyBeanMethods = false)
class VideoCleanupJob {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun mediaVideoCleanupJob(jobRepository: JobRepository, cleanup: CleanUpVideosUseCase): Job {
        val step = StepBuilder(STEP, jobRepository)
            .tasklet({ contribution, _ ->
                val report = cleanup.cleanUp()
                contribution.incrementWriteCount((report.markedFailed + report.removed).toLong())
                log.info("Video cleanup: marked failed {}, removed {}, failed {}", report.markedFailed, report.removed, report.failed)
                check(report.failed == 0) { "${report.failed} videos could not be cleaned up; they stay for the next run" }
                RepeatStatus.FINISHED
            }, ResourcelessTransactionManager())
            .build()
        return JobBuilder(NAME, jobRepository).start(step).build()
    }

    companion object {
        const val NAME = "mediaVideoCleanup"
        private const val STEP = "mediaVideoCleanupStep"
    }
}
