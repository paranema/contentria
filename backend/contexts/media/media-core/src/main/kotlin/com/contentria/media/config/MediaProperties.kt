package com.contentria.media.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("contentria.media")
data class MediaProperties(
    /** What image addresses start with: the bucket's public domain, e.g. https://images.contentria.com. */
    val publicBaseUrl: String,
    val uploadUrlTtl: Duration = Duration.ofMinutes(10),
    val storage: Storage,
) {
    /** An S3-compatible bucket. For Cloudflare R2 the region is "auto". */
    data class Storage(
        val endpoint: URI,
        val bucket: String,
        val accessKeyId: String,
        val secretAccessKey: String,
        val region: String = "auto",
        val pathStyle: Boolean = true,
    )
}
