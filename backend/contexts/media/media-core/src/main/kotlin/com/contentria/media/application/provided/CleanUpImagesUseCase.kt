package com.contentria.media.application.provided

/** docs/contexts/media/01-rules.md, 흐름 6. Run by the system, a batch of the oldest at a time. */
interface CleanUpImagesUseCase {
    fun cleanUp(): CleanupReport
}

/** [failed] images keep their records and are tried again on the next run. */
data class CleanupReport(val removed: Int, val failed: Int)
