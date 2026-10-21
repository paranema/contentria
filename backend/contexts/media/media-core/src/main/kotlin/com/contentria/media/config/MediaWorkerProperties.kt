package com.contentria.media.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.nio.file.Path
import java.time.Duration

/** The worker's settings (docs/operations/cloudflare-video-pipeline.md, 3). No secret has a default. */
@ConfigurationProperties("contentria.media.worker")
data class MediaWorkerProperties(
    val queue: Queue,
    val transcode: Transcode = Transcode(),
) {
    /** Cloudflare Queues' http_pull consumer. [apiToken] needs Queues edit on the account. */
    data class Queue(
        val accountId: String,
        val queueId: String,
        val apiToken: String,
        val baseUrl: URI = URI.create("https://api.cloudflare.com/client/v4"),
        /** Past the longest transcode, so a live job is never handed out twice. */
        val visibilityTimeout: Duration = Duration.ofMinutes(30),
        /** One job at a time per worker. */
        val batchSize: Int = 1,
    )

    data class Transcode(
        val ffmpegPath: String = "ffmpeg",
        val ffprobePath: String = "ffprobe",
        /** 01-rules: 5 minutes. */
        val maxDuration: Duration = Duration.ofMinutes(5),
        val segmentSeconds: Int = 6,
        /** One ffmpeg run at most; past the worst real transcode. */
        val processTimeout: Duration = Duration.ofMinutes(20),
        /** Scratch space for the original and the outputs; the JVM's temporary directory when null. */
        val workDir: Path? = null,
    )
}
