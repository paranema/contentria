package com.contentria.media.adapter.provided.batch

import com.contentria.media.application.provided.CleanUpImagesUseCase
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
 * docs/contexts/media/01-rules.md, 흐름 6, as a one-shot job that apps/batch runs on a schedule.
 * The step itself is not transactional: every image has its own transaction in the use case.
 * Any image that could not be removed fails the job, so the schedule's run shows it; the image is
 * tried again on the next run.
 */
@Configuration(proxyBeanMethods = false)
class ImageCleanupJob {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun mediaImageCleanupJob(jobRepository: JobRepository, cleanup: CleanUpImagesUseCase): Job {
        val step = StepBuilder(STEP, jobRepository)
            .tasklet({ contribution, _ ->
                val report = cleanup.cleanUp()
                contribution.incrementWriteCount(report.removed.toLong())
                log.info("Image cleanup: removed {}, failed {}", report.removed, report.failed)
                check(report.failed == 0) { "${report.failed} images could not be cleaned up; they stay for the next run" }
                RepeatStatus.FINISHED
            }, ResourcelessTransactionManager())
            .build()
        return JobBuilder(NAME, jobRepository).start(step).build()
    }

    companion object {
        const val NAME = "mediaImageCleanup"
        private const val STEP = "mediaImageCleanupStep"
    }
}
