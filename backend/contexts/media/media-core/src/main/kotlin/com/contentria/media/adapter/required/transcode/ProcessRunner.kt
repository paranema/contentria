package com.contentria.media.adapter.required.transcode

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs ffmpeg and ffprobe. A port of its own so tests can run them where ffmpeg is installed. */
fun interface ProcessRunner {
    fun run(command: List<String>, timeout: Duration): ProcessResult
}

data class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val succeeded: Boolean get() = exitCode == 0
}

/** The binaries on this machine, as the worker's container image has them. */
class LocalProcessRunner : ProcessRunner {

    override fun run(command: List<String>, timeout: Duration): ProcessResult {
        val process = ProcessBuilder(command).start()
        // Both streams are drained while the process runs, so a full pipe never blocks it.
        val stdout = CompletableFuture.supplyAsync({ process.inputStream.bufferedReader().readText() }, STREAMS)
        val stderr = CompletableFuture.supplyAsync({ process.errorStream.bufferedReader().readText() }, STREAMS)
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return ProcessResult(-1, "", "timed out after $timeout")
        }
        return ProcessResult(process.exitValue(), stdout.join(), stderr.join())
    }

    private companion object {
        val STREAMS = Executors.newVirtualThreadPerTaskExecutor()
    }
}
