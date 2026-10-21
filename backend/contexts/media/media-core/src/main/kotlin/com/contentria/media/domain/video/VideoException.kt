package com.contentria.media.domain.video

import com.contentria.shared.exception.DomainException

sealed class VideoException(message: String) : DomainException(message) {

    /** 01-rules 흐름 V1의 1번 */
    class UnsupportedFormat :
        VideoException("Videos are MP4, MOV, WebM or MKV")

    /** 01-rules 흐름 V1의 2번 */
    class InvalidSize :
        VideoException("A video is 1 byte to ${Video.MAX_SIZE_BYTES} bytes")

    class InvalidFileName :
        VideoException("A file name is 1-${Video.FILE_NAME_MAX_LENGTH} characters")

    /** A transcoder may only finish what it started. */
    class NotTranscoding :
        VideoException("Only a video being transcoded can be completed")
}
