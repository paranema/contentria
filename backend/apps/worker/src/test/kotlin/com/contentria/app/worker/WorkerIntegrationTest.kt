package com.contentria.app.worker

import com.contentria.media.adapter.required.transcode.ProcessResult
import com.contentria.media.adapter.required.transcode.ProcessRunner
import com.contentria.media.application.required.QueuedObject
import com.contentria.media.application.required.VideoQueue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

/**
 * The worker against a real PostgreSQL, S3 API and ffmpeg, with the queue played by [FakeVideoQueue].
 * The schedule is set far off so that only the tests poll. A subclass must not add its own
 * @DynamicPropertySource.
 */
@SpringBootTest(
    properties = [
        "contentria.media.worker.poll-interval=1h",
        "contentria.media.worker.queue.account-id=test-account",
        "contentria.media.worker.queue.queue-id=test-queue",
        // Short enough that a test can make a longer video in a moment
        "contentria.media.worker.transcode.max-duration=3s",
    ],
)
@Import(WorkerIntegrationTest.Doubles::class)
abstract class WorkerIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    class Doubles {

        @Bean
        @Primary
        fun fakeVideoQueue() = FakeVideoQueue()

        /** ffmpeg and ffprobe inside the ffmpeg container; the paths are the same there. */
        @Bean
        @Primary
        fun containerProcessRunner() = ProcessRunner { command, _ ->
            val result = WorkerTestEnvironment.ffmpeg.execInContainer(*command.toTypedArray())
            ProcessResult(result.exitCode, result.stdout, result.stderr)
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            WorkerTestEnvironment.register(registry)
            // Never sent anywhere: the queue is the fake one
            registry.add("contentria.media.worker.queue.api-token") { UUID.randomUUID().toString() }
        }
    }
}

/** Hands out what a test sends and records what the worker acknowledges. */
class FakeVideoQueue : VideoQueue {

    private val waiting = mutableListOf<QueuedObject>()
    val acked = mutableListOf<String>()
    var failPulls = false

    @Synchronized
    fun send(key: String, action: String = "PutObject"): String =
        UUID.randomUUID().toString().also { waiting += QueuedObject(it, key, action) }

    @Synchronized
    override fun pull(): List<QueuedObject> {
        check(!failPulls) { "queue unavailable" }
        return waiting.toList().also { waiting.clear() }
    }

    @Synchronized
    override fun ack(receipt: String) {
        acked += receipt
    }
}
