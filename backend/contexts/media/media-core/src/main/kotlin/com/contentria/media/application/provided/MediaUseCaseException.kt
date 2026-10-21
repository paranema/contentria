package com.contentria.media.application.provided

import com.contentria.shared.exception.DomainException

/** Failures decided by a use case rather than by a single image. */
sealed class MediaUseCaseException(message: String) : DomainException(message) {

    /** Someone else's image answers as one that does not exist (01-rules 흐름 2의 1번, 흐름 5의 1번). */
    class ImageNotFound :
        MediaUseCaseException("No such image")

    /** 01-rules 흐름 1의 3번 */
    class DailyLimitReached :
        MediaUseCaseException("A member uploads at most 100MB a day")

    /** 01-rules 흐름 2의 3번: the browser has not finished putting the file. */
    class UploadMissing :
        MediaUseCaseException("The file is not in the storage yet")

    /** 01-rules 흐름 2의 4번: the file is not what was declared. The image is gone. */
    class ContentMismatch :
        MediaUseCaseException("The file is not the declared image format")
}
