package com.contentria.app.batch

import com.contentria.media.config.MediaBatchConfiguration
import com.contentria.media.config.MediaCoreConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.time.Clock
import kotlin.system.exitProcess

/**
 * One-shot jobs (docs/architecture/01-module-structure.md, 배포 단위). A schedule starts the process
 * with the job's name (`--spring.batch.job.name=...`); it runs that job and exits with its outcome.
 */
@SpringBootApplication
@Import(
    MediaCoreConfiguration::class,
    MediaBatchConfiguration::class,
)
class BatchApplication {

    /** One clock for every context this process hosts. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
    val context = runApplication<BatchApplication>(*args) {
        setWebApplicationType(WebApplicationType.NONE)
    }
    exitProcess(SpringApplication.exit(context))
}
