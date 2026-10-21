package com.contentria.media.adapter.provided.queue

import com.contentria.media.application.provided.TranscodeVideoUseCase
import com.contentria.media.application.required.QueuedObject
import com.contentria.media.application.required.VideoQueue
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * Pulls the bucket's notices and hands each to the transcoding (01-rules 흐름 V2). A notice is
 * acknowledged once handled; one that failed transiently is left for the queue to send again.
 */
class VideoQueuePoller(
    private val queue: VideoQueue,
    private val transcoding: TranscodeVideoUseCase,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${contentria.media.worker.poll-interval:5s}")
    fun poll() {
        val messages = try {
            queue.pull()
        } catch (e: RuntimeException) {
            log.warn("Video queue pull failed; trying again on the next tick", e)
            return
        }
        messages.forEach(::handle)
    }

    private fun handle(message: QueuedObject) {
        if (message.action !in NEW_OBJECT || !message.key.startsWith(RAW_PREFIX)) {
            queue.ack(message.receipt)
            return
        }
        try {
            transcoding.handle(message.key)
            queue.ack(message.receipt)
        } catch (e: RuntimeException) {
            log.warn("Transcoding {} failed for now; the queue sends it again", message.key, e)
        }
    }

    private companion object {
        val NEW_OBJECT = setOf("PutObject", "CompleteMultipartUpload")
        const val RAW_PREFIX = "raw/"
    }
}
