package com.contentria.media.adapter.required.queue

import com.contentria.media.application.required.QueuedObject
import com.contentria.media.application.required.VideoQueue
import com.contentria.media.config.MediaWorkerProperties
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Cloudflare Queues over its REST API, as an http_pull consumer (docs/operations/cloudflare-video-pipeline.md).
 * Each message's body is the bucket's event notification; only the object key and the action are read.
 * Timeouts are set: the worker polls on one thread, and a hung call would stop it for good.
 */
class CloudflareVideoQueue(private val properties: MediaWorkerProperties.Queue) : VideoQueue {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val json = JsonMapper.builder().build()
    private val base = "${properties.baseUrl.toString().trimEnd('/')}/accounts/${properties.accountId}/queues/${properties.queueId}/messages"

    override fun pull(): List<QueuedObject> {
        val body = json.writeValueAsString(
            mapOf("visibility_timeout_ms" to properties.visibilityTimeout.toMillis(), "batch_size" to properties.batchSize),
        )
        val messages = json.readTree(send("$base/pull", body)).path("result").path("messages")
        return messages.mapNotNull { message ->
            val receipt = message.path("lease_id").asString(null) ?: return@mapNotNull null
            val event = message.path("body").let { if (it.isString) json.readTree(it.asString()) else it }
            QueuedObject(receipt, event.path("object").path("key").asString(""), event.path("action").asString(""))
        }
    }

    override fun ack(receipt: String) {
        send("$base/ack", json.writeValueAsString(mapOf("acks" to listOf(mapOf("lease_id" to receipt)))))
    }

    private fun send(url: String, body: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer ${properties.apiToken}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        // The status only: the body may echo the request, token aside.
        check(response.statusCode() in 200..299) { "Cloudflare Queues answered ${response.statusCode()} for ${URI.create(url).path}" }
        return response.body()
    }
}
