package com.contentria.media.domain.image

/** docs/contexts/media/02-implementation.md, 애그리거트 경계. A rejected or deleted image has no status: it is gone. */
enum class MediaStatus {

    /** An upload address was handed out; the file is not checked yet. */
    UPLOADING,

    /** The file was checked and may be attached to posts. */
    READY,
}
