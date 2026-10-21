package com.contentria.app.api

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import javax.imageio.ImageIO

/** An image the member prepared, put to the storage and completed. */
data class UploadedImage(val id: UUID, val url: String)

/** Prepares, puts and completes a small PNG, as the editor does before it shows the image. */
fun TestBrowser.uploadPng(): UploadedImage {
    val png = pngBytes()
    val prepared = post("/media/uploads", """{"contentType":"image/png","sizeBytes":${png.size},"fileName":"a.png"}""")
    check(prepared.statusCode() == 201) { prepared.body() }
    check(putToStorage(prepared.field("uploadUrl"), "image/png", png).statusCode() == 200)
    check(post("/media/${prepared.field("mediaId")}/complete", "").statusCode() == 200)
    return UploadedImage(UUID.fromString(prepared.field("mediaId")), prepared.field("imageUrl"))
}

/** What the browser does with an upload address. */
fun putToStorage(uploadUrl: String, contentType: String, bytes: ByteArray): HttpResponse<String> =
    storageHttp.send(
        HttpRequest.newBuilder(URI.create(uploadUrl)).header("Content-Type", contentType).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

fun pngBytes(): ByteArray = encodeImage(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png")

fun encodeImage(image: BufferedImage, format: String): ByteArray =
    ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()

private val storageHttp: HttpClient = HttpClient.newHttpClient()
