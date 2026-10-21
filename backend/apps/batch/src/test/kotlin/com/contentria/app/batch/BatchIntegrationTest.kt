package com.contentria.app.batch

import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * The one batch application every job test runs against, sharing one context. No job runs at
 * start-up; a test starts the one it checks. A subclass must not add its own @DynamicPropertySource.
 */
@SpringBootTest(properties = ["spring.batch.job.enabled=false"])
abstract class BatchIntegrationTest {

    @Autowired
    private lateinit var jobOperator: JobOperator

    /** Runs [job] once with fresh parameters, as each scheduled run is. */
    protected fun run(job: Job): BatchStatus =
        jobOperator.start(job, JobParametersBuilder().addLong("run", System.nanoTime()).toJobParameters()).status

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = BatchTestEnvironment.register(registry)
    }
}
