package com.contentria.media.adapter.required.image

import com.contentria.media.application.required.ImageCleaner
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Decodes to pixels and encodes again with the JDK alone: EXIF, IPTC, XMP and thumbnails do not
 * survive (docs/design-notes/media-upload-hardening.md, 3). Quality 0.9 is not told apart from the
 * original at web sizes.
 */
@Component
class ImageIoCleaner : ImageCleaner {

    override fun jpegWithoutMetadata(jpeg: ByteArray): ByteArray? {
        val pixels = try {
            ImageIO.read(ByteArrayInputStream(jpeg))
        } catch (e: IOException) {
            // ImageIO cannot decode some valid JPEGs (CMYK among them); they are refused like a wrong file.
            null
        } ?: return null

        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val output = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(output).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = QUALITY
                }
                writer.write(null, IIOImage(pixels, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return output.toByteArray()
    }

    private companion object {
        const val QUALITY = 0.9f
    }
}
