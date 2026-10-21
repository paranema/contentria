package com.contentria.media.domain.image

/**
 * The image formats a post may carry (docs/contexts/media/01-rules.md, 처음 정하는 값), and how
 * each one's file begins. A file is what its first bytes say, not what the browser declared.
 */
enum class ImageFormat(val contentType: String, val extension: String) {

    /** FF D8 FF */
    JPEG("image/jpeg", "jpg") {
        override fun matches(header: ByteArray) = header.startsWith(0xFF, 0xD8, 0xFF)
    },

    /** 89 50 4E 47 0D 0A 1A 0A */
    PNG("image/png", "png") {
        override fun matches(header: ByteArray) = header.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    },

    /** "RIFF", four bytes of size, then "WEBP" */
    WEBP("image/webp", "webp") {
        override fun matches(header: ByteArray) =
            header.startsWith(0x52, 0x49, 0x46, 0x46) && header.size >= 12 && header.copyOfRange(8, 12).startsWith(0x57, 0x45, 0x42, 0x50)
    },

    /** "GIF87a" or "GIF89a" */
    GIF("image/gif", "gif") {
        override fun matches(header: ByteArray) =
            header.startsWith(0x47, 0x49, 0x46, 0x38, 0x37, 0x61) || header.startsWith(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
    },
    ;

    /** Whether a file beginning with [header] (at least [HEADER_BYTES] of it, if the file has them) is this format. */
    abstract fun matches(header: ByteArray): Boolean

    companion object {

        /** Enough of a file to tell every format apart. */
        const val HEADER_BYTES = 12

        /** The format declared as [contentType], or null for one a post may not carry. */
        fun of(contentType: String): ImageFormat? = entries.firstOrNull { it.contentType == contentType.trim().lowercase() }

        private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
    }
}
