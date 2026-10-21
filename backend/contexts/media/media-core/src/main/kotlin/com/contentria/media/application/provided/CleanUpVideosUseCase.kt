package com.contentria.media.application.provided

/** docs/contexts/media/01-rules.md, 흐름 V5. Run by the system, a batch of the oldest at a time. */
interface CleanUpVideosUseCase {
    fun cleanUp(): VideoCleanupReport
}

/**
 * [markedFailed] videos had been transcoding too long; [removed] went with their files. [failed]
 * videos keep their records and are tried again on the next run.
 */
data class VideoCleanupReport(val markedFailed: Int, val removed: Int, val failed: Int)
