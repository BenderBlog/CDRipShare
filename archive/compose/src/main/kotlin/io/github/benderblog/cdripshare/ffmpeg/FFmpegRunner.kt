package io.github.benderblog.cdripshare.ffmpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

class FFmpegRunner(
    private val ffmpegPath: Path,
    private val ffprobePath: Path = java.nio.file.Paths.get(ffmpegPath.toString().replace("ffmpeg", "ffprobe"))
) {
    @Volatile
    private var currentProcess: Process? = null

    fun destroyCurrentProcess() {
        currentProcess?.let {
            if (it.isAlive) {
                it.destroyForcibly()
            }
        }
        currentProcess = null
    }

    suspend fun execute(
        args: List<String>,
        totalDurationUs: Long = 0L,
        onProgress: (Float) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        val job = coroutineContext[Job]!!
        val cmd = listOf(ffmpegPath.toString()) + args
        onLog("执行: ${cmd.joinToString(" ")}")

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(false)
            .start()

        currentProcess = process

        try {
            // stderr → log only (FFmpeg 的编码信息、警告等)
            val stderrThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (job.isActive) {
                            onLog("[stderr] $line")
                        }
                    }
                }
            }
            stderrThread.isDaemon = true
            stderrThread.start()

            // stdout → -progress pipe:1 输出，解析进度
            var lastOutTimeUs = 0L
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    ensureActive()
                    val parsed = parseProgressLine(line) ?: return@forEach
                    when (parsed.first) {
                        "out_time_us" -> {
                            lastOutTimeUs = parsed.second.toLongOrNull() ?: 0L
                            if (totalDurationUs > 0) {
                                val progress = (lastOutTimeUs.toFloat() / totalDurationUs).coerceIn(0f, 1f)
                                onProgress(progress)
                            }
                        }
                        "progress" -> {
                            if (parsed.second == "end") {
                                onProgress(1f)
                            }
                        }
                    }
                }
            }

            stderrThread.join(5000)
            process.waitFor()
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
            currentProcess = null
        }
    }

    suspend fun probeDuration(file: java.io.File): Double =
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(
                ffprobePath.toString(),
                "-v", "quiet",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.absolutePath
            ).start()

            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output.toDoubleOrNull()
                ?: throw IllegalArgumentException("无法获取音频时长: ${file.name}")
        }

    suspend fun probeCodec(file: java.io.File): String =
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(
                ffprobePath.toString(),
                "-v", "quiet",
                "-select_streams", "a:0",
                "-show_entries", "stream=codec_name",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.absolutePath
            ).start()

            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output.ifEmpty { throw IllegalArgumentException("无法检测音频编码: ${file.name}") }
        }

    private fun parseProgressLine(line: String): Pair<String, String>? {
        val idx = line.indexOf('=')
        if (idx <= 0) return null
        return line.substring(0, idx).trim() to line.substring(idx + 1).trim()
    }
}
