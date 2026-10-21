package com.contentria.media.domain.image

import com.contentria.shared.exception.DomainException

sealed class MediaException(message: String) : DomainException(message) {

    /** 01-rules 흐름 1의 1번 */
    class UnsupportedFormat :
        MediaException("Images are JPEG, PNG, WebP or GIF")

    /** 01-rules 흐름 1의 2번 */
    class InvalidSize :
        MediaException("An image is 1 byte to ${Media.MAX_SIZE_BYTES} bytes")

    class InvalidFileName :
        MediaException("A file name is 1-${Media.FILE_NAME_MAX_LENGTH} characters")

    /** 01-rules 흐름 5의 2번 */
    class Attached :
        MediaException("An image used by a post is released by removing it from the post")
}
