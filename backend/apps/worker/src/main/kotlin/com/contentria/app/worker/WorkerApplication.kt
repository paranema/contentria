package com.contentria.app.worker

import com.contentria.media.config.MediaCoreConfiguration
import com.contentria.media.config.MediaQueueConfiguration
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.time.Clock

/**
 * The long-running video worker (docs/architecture/01-module-structure.md, 배포 단위). It pulls the
 * bucket's notices from the queue and transcodes each upload; it serves no HTTP. Its image must
 * carry ffmpeg and ffprobe.
 */
@SpringBootApplication
@Import(MediaCoreConfiguration::class, MediaQueueConfiguration::class)
class WorkerApplication {

    /** One clock for every context this process hosts. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
    runApplication<WorkerApplication>(*args) {
        setWebApplicationType(WebApplicationType.NONE)
    }
}
