package com.contentria.media.application.provided

/** docs/contexts/media/01-rules.md, 흐름 V2. Run by the worker, once per notice from the bucket. */
interface TranscodeVideoUseCase {
    /**
     * Handles the bucket's notice about [rawKey]. Returning normally means the notice is done with,
     * whatever came of it; an exception means a transient failure and the notice should come again.
     */
    fun handle(rawKey: String): TranscodingOutcome
}

enum class TranscodingOutcome { SKIPPED, COMPLETED, FAILED }
