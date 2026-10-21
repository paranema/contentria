package com.contentria.app.api

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import javax.imageio.ImageIO

/** Image uploads through a real S3 API (media 01-rules 흐름 1, 2, 5). */
class MediaUploadIntegrationTest : ApiIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var codes: RecordingCodeSender

    private val http: HttpClient = HttpClient.newHttpClient()

    @Test
    @DisplayName("업로드를 준비하고 저장소에 올린 뒤 완료하면, 그 주소로 누구나 이미지를 본다")
    fun `prepare, put, complete`() {
        val member = member()
        val png = png()

        val prepared = member.post("/media/uploads", request("image/png", png.size, "그림.png"))
        assertThat(prepared.statusCode()).isEqualTo(201)
        val id = prepared.field("mediaId")
        val imageUrl = prepared.field("imageUrl")
        assertThat(imageUrl).isEqualTo("${ApiTestEnvironment.mediaPublicBaseUrl}/images/$id.png")
        assertThat(prepared.body()).contains("\"uploadUrl\":\"http", "\"expiresAt\"")
        assertThat(status(id)).isEqualTo("UPLOADING")

        assertThat(put(prepared.field("uploadUrl"), "image/png", png).statusCode()).isEqualTo(200)
        val completed = member.post("/media/$id/complete", "")

        assertThat(completed.statusCode()).isEqualTo(200)
        assertThat(completed.body()).contains("\"imageUrl\":\"$imageUrl\"", "\"contentType\":\"image/png\"", "\"fileName\":\"그림.png\"")
        assertThat(status(id)).isEqualTo("READY")
        assertThat(ApiTestEnvironment.row("select unattached_since from media.media where id = ?", UUID.fromString(id))!!["unattached_since"]).isNotNull()
        val served = http.send(HttpRequest.newBuilder(URI.create(imageUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())
        assertThat(served.statusCode()).isEqualTo(200)
        assertThat(served.body()).isEqualTo(png)

        assertThat(member.post("/media/$id/complete", "").statusCode()).isEqualTo(200)
    }

    @Test
    @DisplayName("JPEG는 완료할 때 사진 정보를 지운 파일로 같은 주소에 다시 저장한다")
    fun `jpeg metadata goes`() {
        val member = member()
        val jpeg = jpegWithExif()
        assertThat(String(jpeg, Charsets.ISO_8859_1)).contains(SECRET)

        val prepared = member.post("/media/uploads", request("image/jpeg", jpeg.size, "photo.jpg"))
        put(prepared.field("uploadUrl"), "image/jpeg", jpeg)
        assertThat(member.post("/media/${prepared.field("mediaId")}/complete", "").statusCode()).isEqualTo(200)

        val stored = stored("images/${prepared.field("mediaId")}.jpg")
        assertThat(String(stored, Charsets.ISO_8859_1)).doesNotContain(SECRET, "Exif")
        assertThat(ImageIO.read(ByteArrayInputStream(stored)).width).isEqualTo(8)
    }

    @Test
    @DisplayName("선언한 형식과 파일의 앞부분이 다르면 거부하고, 기록과 파일을 지운다")
    fun `a file that is not what it says`() {
        val member = member()
        val html = "<!DOCTYPE html><script>alert(1)</script>".toByteArray()

        val prepared = member.post("/media/uploads", request("image/png", html.size, "x.png"))
        val id = prepared.field("mediaId")
        put(prepared.field("uploadUrl"), "image/png", html)
        val refused = member.post("/media/$id/complete", "")

        assertThat(refused.statusCode()).isEqualTo(400)
        assertThat(refused.field("code")).isEqualTo("IMAGE_CONTENT_MISMATCH")
        assertThat(ApiTestEnvironment.row("select id from media.media where id = ?", UUID.fromString(id))).isNull()
        assertThatThrownBy { stored("images/$id.png") }.isInstanceOf(NoSuchKeyException::class.java)
        assertThat(member.post("/media/$id/complete", "").field("code")).isEqualTo("IMAGE_NOT_FOUND")
    }

    @Test
    @DisplayName("저장소는 업로드 주소에 서명한 형식과 크기가 아닌 파일을 받지 않는다")
    fun `the storage holds the upload to its declaration`() {
        val member = member()
        val png = png()
        val prepared = member.post("/media/uploads", request("image/png", png.size, "a.png"))

        assertThat(put(prepared.field("uploadUrl"), "image/png", png + png).statusCode()).isNotEqualTo(200)
        assertThat(put(prepared.field("uploadUrl"), "text/html", png).statusCode()).isNotEqualTo(200)
        assertThat(member.post("/media/${prepared.field("mediaId")}/complete", "").field("code")).isEqualTo("UPLOAD_NOT_FINISHED")
    }

    @Test
    @DisplayName("올리기 전에 완료하면 거부하고, 남의 이미지는 없는 이미지와 같다")
    fun `completing too early or someone else's`() {
        val member = member()
        val prepared = member.post("/media/uploads", request("image/gif", 100, "a.gif"))
        val id = prepared.field("mediaId")

        val early = member.post("/media/$id/complete", "")
        assertThat(early.statusCode()).isEqualTo(409)
        assertThat(early.field("code")).isEqualTo("UPLOAD_NOT_FINISHED")
        assertThat(status(id)).isEqualTo("UPLOADING")

        val other = member().post("/media/$id/complete", "")
        assertThat(other.statusCode()).isEqualTo(404)
        assertThat(other.field("code")).isEqualTo("IMAGE_NOT_FOUND")
        assertThat(member.post("/media/${UUID.randomUUID()}/complete", "").statusCode()).isEqualTo(404)
    }

    @Test
    @DisplayName("허용하지 않은 형식, 10MB를 넘는 크기, 빠진 값, 로그인하지 않은 요청은 준비하지 않는다")
    fun `preparation refusals`() {
        val member = member()

        assertThat(member.post("/media/uploads", request("image/svg+xml", 10, "a.svg")).field("code")).isEqualTo("UNSUPPORTED_IMAGE_FORMAT")
        assertThat(member.post("/media/uploads", request("image/png", 10L * 1024 * 1024 + 1, "a.png")).field("code")).isEqualTo("IMAGE_TOO_LARGE")
        assertThat(member.post("/media/uploads", """{"contentType":"image/png"}""").field("code")).isEqualTo("INVALID_REQUEST")
        assertThat(TestBrowser(port).post("/media/uploads", request("image/png", 10, "a.png")).statusCode()).isEqualTo(401)
    }

    @Test
    @DisplayName("한 회원은 하루에 100MB까지 업로드를 준비한다")
    fun `daily limit`() {
        val member = member()
        val tenMegabytes = 10L * 1024 * 1024

        repeat(10) { assertThat(member.post("/media/uploads", request("image/png", tenMegabytes, "$it.png")).statusCode()).isEqualTo(201) }
        val refused = member.post("/media/uploads", request("image/png", 1, "one-more.png"))

        assertThat(refused.statusCode()).isEqualTo(429)
        assertThat(refused.field("code")).isEqualTo("DAILY_UPLOAD_LIMIT")
        assertThat(member().post("/media/uploads", request("image/png", tenMegabytes, "other.png")).statusCode()).isEqualTo(201)
    }

    @Test
    @DisplayName("글에 쓰이지 않는 내 이미지는 지울 수 있고, 글에 연결된 이미지와 남의 이미지는 지울 수 없다")
    fun `deleting`() {
        val member = member()
        val loose = uploaded(member)
        val used = uploaded(member)
        ApiTestEnvironment.execute("insert into media.media_attachments (media_id, post_id) values (?, ?)", UUID.fromString(used), UUID.randomUUID())

        assertThat(member().delete("/media/$loose").statusCode()).isEqualTo(404)
        val inUse = member.delete("/media/$used")
        assertThat(inUse.statusCode()).isEqualTo(409)
        assertThat(inUse.field("code")).isEqualTo("IMAGE_IN_USE")
        assertThat(stored("images/$used.png")).isNotEmpty()

        assertThat(member.delete("/media/$loose").statusCode()).isEqualTo(204)
        assertThat(ApiTestEnvironment.row("select id from media.media where id = ?", UUID.fromString(loose))).isNull()
        assertThatThrownBy { stored("images/$loose.png") }.isInstanceOf(NoSuchKeyException::class.java)
        assertThat(member.delete("/media/$loose").statusCode()).isEqualTo(404)
    }

    private fun member(): TestBrowser = TestBrowser(port).also { it.signUpAsNewMember(codes) }

    /** A PNG prepared, put and completed; returns its id. */
    private fun uploaded(member: TestBrowser): String = member.uploadPng().id.toString()

    private fun request(contentType: String, size: Number, fileName: String) =
        """{"contentType":"$contentType","sizeBytes":$size,"fileName":"$fileName"}"""

    private fun put(uploadUrl: String, contentType: String, bytes: ByteArray): HttpResponse<String> = putToStorage(uploadUrl, contentType, bytes)

    private fun status(id: String): Any? = ApiTestEnvironment.row("select status from media.media where id = ?", UUID.fromString(id))!!["status"]

    private fun stored(key: String): ByteArray =
        ApiTestEnvironment.storage.getObjectAsBytes { it.bucket(ApiTestEnvironment.MEDIA_BUCKET).key(key) }.asByteArray()

    private fun png(): ByteArray = pngBytes()

    /** A JPEG with an APP1 Exif segment right after its start marker, carrying [SECRET]. */
    private fun jpegWithExif(): ByteArray {
        val plain = encodeImage(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg")
        val payload = "Exif\u0000\u0000MM\u0000*\u0000\u0000\u0000\u0008$SECRET".toByteArray(Charsets.ISO_8859_1)
        val length = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return plain.copyOfRange(0, 2) + app1 + plain.copyOfRange(2, plain.size)
    }

    private companion object {
        const val SECRET = "GPS 37.5665N 126.9780E"
    }
}
