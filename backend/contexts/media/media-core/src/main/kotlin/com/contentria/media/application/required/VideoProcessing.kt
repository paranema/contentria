package com.contentria.media.application.required

import com.contentria.media.domain.video.TranscodedVideo
import java.util.UUID

/**
 * The queue the bucket notifies when an original lands (docs/contexts/media/02-implementation.md, 영상).
 * A message not acknowledged comes back after the visibility timeout; after three tries the queue
 * puts it aside (the dead-letter queue).
 */
interface VideoQueue {
    fun pull(): List<QueuedObject>
    fun ack(receipt: String)
}

/** [action] is the bucket's: "PutObject" or "CompleteMultipartUpload" for a new object. */
data class QueuedObject(val receipt: String, val key: String, val action: String)

/** Turns an original into HLS and a poster under the video's output prefix. */
interface Transcoder {
    /** Throws [NotAVideo] for input that no retry can fix; any other exception is worth retrying. */
    fun transcode(job: TranscodeJob): TranscodedVideo
}

data class TranscodeJob(val videoId: UUID, val rawKey: String, val outputPrefix: String)

/** 01-rules 흐름 V2의 3번: not a video, or too long. */
class NotAVideo(reason: String) : RuntimeException(reason)
