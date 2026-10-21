package com.contentria.media.application.required

import java.net.URI
import java.nio.file.Path
import java.time.Duration

/**
 * The bucket the images live in (docs/contexts/media/02-implementation.md, 포트와 어댑터). Keys are
 * the media context's own; the adapter knows nothing of images.
 */
interface ObjectStorage {

    /** An address the browser may PUT exactly one object to: this key, type and size, for [validFor]. */
    fun uploadUrl(key: String, contentType: String, sizeBytes: Long, validFor: Duration): URI

    /** The whole object, or null when there is none. */
    fun read(key: String): ByteArray?

    fun write(key: String, bytes: ByteArray, contentType: String)

    /** Succeeds when there is nothing to delete. */
    fun delete(key: String)

    /** Streams the object to [target] without holding it in memory: a video is up to 500MB. */
    fun download(key: String, target: Path)

    fun upload(key: String, source: Path, contentType: String)

    /** Every object whose key starts with [prefix], such as a video's transcoded outputs. */
    fun deletePrefix(prefix: String)
}

/** Re-encodes an image so that only its pixels remain (01-rules 흐름 2의 5번). */
interface ImageCleaner {

    /** The JPEG without its metadata, or null when it cannot be decoded as an image. */
    fun jpegWithoutMetadata(jpeg: ByteArray): ByteArray?
}
