package com.contentria.media.domain.video

/** The video formats a post may carry (docs/contexts/media/01-rules.md, 영상의 처음 정하는 값). */
enum class VideoFormat(val contentType: String, val extension: String) {
    MP4("video/mp4", "mp4"),
    MOV("video/quicktime", "mov"),
    WEBM("video/webm", "webm"),
    MKV("video/x-matroska", "mkv"),
    ;

    companion object {
        /** The format declared as [contentType], or null for one a post may not carry. */
        fun of(contentType: String): VideoFormat? = entries.firstOrNull { it.contentType == contentType.trim().lowercase() }
    }
}
