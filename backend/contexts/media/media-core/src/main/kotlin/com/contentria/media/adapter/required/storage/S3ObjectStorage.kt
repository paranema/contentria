package com.contentria.media.adapter.required.storage

import com.contentria.media.application.required.ObjectStorage
import com.contentria.media.config.MediaProperties
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * The bucket over the S3 API, which Cloudflare R2 speaks (and MinIO, in tests). The presigned
 * upload signs the content type and length, so the storage refuses any other file.
 */
@Component
class S3ObjectStorage(
    private val s3: S3Client,
    private val presigner: S3Presigner,
    properties: MediaProperties,
) : ObjectStorage {

    private val bucket = properties.storage.bucket

    override fun uploadUrl(key: String, contentType: String, sizeBytes: Long, validFor: Duration): URI {
        val put = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).contentLength(sizeBytes).build()
        val request = PutObjectPresignRequest.builder().signatureDuration(validFor).putObjectRequest(put).build()
        return presigner.presignPutObject(request).url().toURI()
    }

    override fun read(key: String): ByteArray? =
        try {
            s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray()
        } catch (e: NoSuchKeyException) {
            null
        }

    override fun write(key: String, bytes: ByteArray, contentType: String) {
        val put = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).contentLength(bytes.size.toLong()).build()
        s3.putObject(put, RequestBody.fromBytes(bytes))
    }

    override fun delete(key: String) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
    }

    override fun download(key: String, target: Path) {
        Files.deleteIfExists(target)
        s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), ResponseTransformer.toFile(target))
    }

    override fun upload(key: String, source: Path, contentType: String) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(), RequestBody.fromFile(source))
    }

    /** A thousand at a time, the most one DeleteObjects takes. */
    override fun deletePrefix(prefix: String) {
        s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build()).forEach { page ->
            val keys = page.contents().map { ObjectIdentifier.builder().key(it.key()).build() }
            if (keys.isNotEmpty()) {
                s3.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket).delete(Delete.builder().objects(keys).build()).build())
            }
        }
    }
}
