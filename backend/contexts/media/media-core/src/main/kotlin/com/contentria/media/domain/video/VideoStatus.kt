package com.contentria.media.domain.video

/** docs/contexts/media/01-rules.md, 영상의 상태. */
enum class VideoStatus {
    /** The upload address was handed out; waiting for the file, or for a transcoder. */
    PENDING,

    /** A transcoder has it. */
    PROCESSING,

    /** Streamable: the HLS outputs and the poster exist. */
    COMPLETED,

    /** Not a video, too long, or stuck too long. Never tried again. */
    FAILED,
    ;

    val isFinal: Boolean get() = this == COMPLETED || this == FAILED
}
