package com.contentria.media.adapter.required.transcode

import com.contentria.media.application.required.NotAVideo
import com.contentria.media.application.required.ObjectStorage
import com.contentria.media.application.required.TranscodeJob
import com.contentria.media.application.required.Transcoder
import com.contentria.media.config.MediaWorkerProperties
import com.contentria.media.domain.video.TranscodedVideo
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

/**
 * ffprobe to check, ffmpeg to make the HLS ladder (720p and 480p, never above the source) and a
 * poster, then the outputs up under the video's prefix (docs/contexts/media/01-rules.md, 영상).
 * A bad input is [NotAVideo]; an ffmpeg failure after a good probe is transient and retried.
 */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class FfmpegTranscoder(
    private val properties: MediaWorkerProperties.Transcode,
    private val processes: ProcessRunner,
    private val storage: ObjectStorage,
) : Transcoder {

    private val json = JsonMapper.builder().build()

    override fun transcode(job: TranscodeJob): TranscodedVideo {
        val work = workDirFor(job)
        try {
            val input = work.resolve("input")
            storage.download(job.rawKey, input)

            val probe = probe(input)
            if (probe.durationMs > properties.maxDuration.toMillis()) {
                throw NotAVideo("Longer than ${properties.maxDuration}: ${probe.durationMs}ms")
            }

            val out = work.resolve("out").createDirectories()
            val ladder = renditionsFor(probe.height)
            run(hls(input, out, ladder, probe.hasAudio), "HLS encode")
            run(poster(input, out.resolve(POSTER), probe.durationMs), "poster")

            Files.walk(out).use { files ->
                files.filter { it.isRegularFile() }.forEach { file ->
                    storage.upload(job.outputPrefix + file.relativeTo(out).toString().replace('\\', '/'), file, contentTypeOf(file))
                }
            }
            return TranscodedVideo(job.outputPrefix + MASTER, job.outputPrefix + POSTER, probe.durationMs, probe.width, probe.height)
        } finally {
            work.deleteRecursively()
        }
    }

    private fun workDirFor(job: TranscodeJob): Path {
        val root = properties.workDir?.createDirectories()
        val prefix = "video-${job.videoId}-"
        return if (root != null) Files.createTempDirectory(root, prefix) else Files.createTempDirectory(prefix)
    }

    private fun probe(input: Path): Probe {
        val result = processes.run(
            listOf(properties.ffprobePath, "-v", "error", "-print_format", "json", "-show_format", "-show_streams", input.toString()),
            Duration.ofMinutes(1),
        )
        if (!result.succeeded) throw NotAVideo("ffprobe could not read it")
        val root = json.readTree(result.stdout)
        val streams = root.path("streams")
        val video = streams.firstOrNull { it.path("codec_type").asString("") == "video" } ?: throw NotAVideo("No video stream")
        val width = video.path("width").asInt(0)
        val height = video.path("height").asInt(0)
        if (width <= 0 || height <= 0) throw NotAVideo("No picture size")
        val seconds = root.path("format").path("duration").asString("0").toDoubleOrNull() ?: 0.0
        return Probe(width, height, (seconds * 1000).toLong(), streams.any { it.path("codec_type").asString("") == "audio" })
    }

    /** The fixed ladder, without any rung above the source; a source below the lowest gets one at its own height. */
    private fun renditionsFor(sourceHeight: Int): List<Rendition> {
        val even = sourceHeight - sourceHeight % 2
        return LADDER.filter { it.height <= even }.ifEmpty { listOf(LADDER.last().copy(height = even.coerceAtLeast(2))) }
    }

    private fun hls(input: Path, out: Path, ladder: List<Rendition>, hasAudio: Boolean): List<String> {
        val args = mutableListOf(properties.ffmpegPath, "-y", "-i", input.toString())
        val split = ladder.indices.joinToString("") { "[v$it]" }
        args += listOf("-filter_complex", "[0:v]split=${ladder.size}$split;" + ladder.indices.joinToString(";") { "[v$it]scale=-2:${ladder[it].height}[out$it]" })
        ladder.forEachIndexed { i, r ->
            args += listOf(
                "-map", "[out$i]", "-c:v:$i", "libx264", "-preset", "medium", "-crf", r.crf.toString(),
                "-maxrate:v:$i", "${r.maxrateKbps}k", "-bufsize:v:$i", "${r.maxrateKbps * 2}k",
                "-g", "48", "-keyint_min", "48", "-sc_threshold", "0",
            )
        }
        if (hasAudio) ladder.forEachIndexed { i, r -> args += listOf("-map", "a:0", "-c:a:$i", "aac", "-b:a:$i", "${r.audioKbps}k", "-ac", "2") }
        args += listOf(
            "-f", "hls", "-hls_time", properties.segmentSeconds.toString(), "-hls_playlist_type", "vod",
            "-hls_segment_type", "fmp4", "-hls_flags", "independent_segments", "-master_pl_name", MASTER,
            "-hls_segment_filename", "$out/stream_%v/segment_%03d.m4s",
            "-var_stream_map", ladder.indices.joinToString(" ") { if (hasAudio) "v:$it,a:$it" else "v:$it" },
            "$out/stream_%v/playlist.m3u8",
        )
        return args
    }

    /** One frame a second in, or halfway through a shorter video, no taller than 720. */
    private fun poster(input: Path, target: Path, durationMs: Long): List<String> {
        val at = minOf(1000L, durationMs / 2) / 1000.0
        return listOf(properties.ffmpegPath, "-y", "-ss", at.toString(), "-i", input.toString(), "-frames:v", "1", "-vf", "scale=-2:'min(720,ih)'", "-q:v", "3", target.toString())
    }

    private fun run(command: List<String>, what: String) {
        val result = processes.run(command, properties.processTimeout)
        // After a good probe a failing ffmpeg is taken as transient: the queue tries again, then sets it aside.
        check(result.succeeded) { "ffmpeg $what failed (exit ${result.exitCode}): ${result.stderr.takeLast(500)}" }
    }

    private fun contentTypeOf(file: Path): String = when (file.extension) {
        "m3u8" -> "application/vnd.apple.mpegurl"
        "m4s" -> "video/iso.segment"
        "mp4" -> "video/mp4"
        "jpg" -> "image/jpeg"
        else -> "application/octet-stream"
    }

    private data class Probe(val width: Int, val height: Int, val durationMs: Long, val hasAudio: Boolean)

    private data class Rendition(val height: Int, val crf: Int, val maxrateKbps: Int, val audioKbps: Int)

    private companion object {
        const val MASTER = "master.m3u8"
        const val POSTER = "poster.jpg"
        val LADDER = listOf(Rendition(720, 23, 3000, 128), Rendition(480, 24, 1500, 96))
    }
}
