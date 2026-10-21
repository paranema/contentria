package com.contentria.media.application.service

import com.contentria.media.domain.image.ImageFormat
import com.contentria.media.domain.image.Media
import java.util.UUID

/**
 * Public addresses of images (02-implementation, 저장소의 주소): the public base followed by the
 * key. Which addresses are this context's is decided here; publishing never knows the base.
 */
class ImageAddresses(publicBaseUrl: String) {

    private val base = publicBaseUrl.trimEnd('/')

    private val ownAddress = Regex(
        "^" + Regex.escape("$base/${Media.KEY_PREFIX}/") +
            "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.(" +
            ImageFormat.entries.joinToString("|") { it.extension } + ")$",
    )

    fun urlOf(media: Media): String = urlOfKey(media.storedKey)

    /** Any object of the bucket as the public domain serves it, a video's playlist and poster included. */
    fun urlOfKey(key: String): String = "$base/$key"

    /** The image an address points to, or null for an address that is not one of ours. */
    fun mediaIdOf(url: String): UUID? = ownAddress.matchEntire(url.trim())?.let { UUID.fromString(it.groupValues[1]) }
}
