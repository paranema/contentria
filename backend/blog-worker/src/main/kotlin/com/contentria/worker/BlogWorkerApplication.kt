package com.contentria.worker

import com.contentria.common.global.config.R2Config
import com.contentria.worker.config.CloudflareProperties
import com.contentria.worker.config.TranscodeProperties
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Long-running worker that transcodes uploaded videos. The work queue is Cloudflare
 * Queue, which this app pulls directly via an `http_pull` consumer (see
 * `backend/docs/operations/cloudflare-video-pipeline.md`). It accesses the `videos` table with
 * JdbcTemplate (no dependency on blog-api's JPA entity), mirroring `blog-batch`.
 *
 * Component scan is scoped to this module so blog-common's web/JPA beans are not pulled in.
 * R2Config is imported explicitly instead — it's self-contained and doesn't drag in
 * blog-common's mail config (unlike CommonConfig, which this module doesn't need).
 */
@SpringBootApplication(scanBasePackages = ["com.contentria.worker"])
@EnableConfigurationProperties(CloudflareProperties::class, TranscodeProperties::class)
@Import(R2Config::class)
@EnableScheduling
class BlogWorkerApplication

fun main(args: Array<String>) {
    runApplication<BlogWorkerApplication>(*args) {
        setWebApplicationType(WebApplicationType.NONE)
    }
}
