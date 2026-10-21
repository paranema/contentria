package com.contentria.media.config

import com.contentria.media.adapter.provided.queue.VideoQueuePoller
import com.contentria.media.adapter.required.queue.CloudflareVideoQueue
import com.contentria.media.adapter.required.transcode.FfmpegTranscoder
import com.contentria.media.adapter.required.transcode.LocalProcessRunner
import com.contentria.media.adapter.required.transcode.ProcessRunner
import com.contentria.media.application.provided.TranscodeVideoUseCase
import com.contentria.media.application.required.ObjectStorage
import com.contentria.media.application.required.Transcoder
import com.contentria.media.application.required.VideoQueue
import com.contentria.media.application.required.VideoRepository
import com.contentria.media.application.service.VideoTranscodingService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * Video transcoding: the queue, ffmpeg and the poller (docs/contexts/media/02-implementation.md, 영상).
 * Loaded by apps/worker only, with [MediaCoreConfiguration]; nothing here is scanned, so no other
 * process needs the worker's settings or ffmpeg.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MediaWorkerProperties::class)
class MediaQueueConfiguration {

    @Bean
    fun mediaVideoQueue(properties: MediaWorkerProperties): VideoQueue = CloudflareVideoQueue(properties.queue)

    @Bean
    fun mediaProcessRunner(): ProcessRunner = LocalProcessRunner()

    @Bean
    fun mediaTranscoder(properties: MediaWorkerProperties, processes: ProcessRunner, storage: ObjectStorage): Transcoder =
        FfmpegTranscoder(properties.transcode, processes, storage)

    @Bean
    fun mediaVideoTranscoding(
        videos: VideoRepository,
        transcoder: Transcoder,
        storage: ObjectStorage,
        transactions: TransactionTemplate,
        clock: Clock,
    ): TranscodeVideoUseCase = VideoTranscodingService(videos, transcoder, storage, transactions, clock)

    @Bean
    fun mediaVideoQueuePoller(queue: VideoQueue, transcoding: TranscodeVideoUseCase) = VideoQueuePoller(queue, transcoding)
}
