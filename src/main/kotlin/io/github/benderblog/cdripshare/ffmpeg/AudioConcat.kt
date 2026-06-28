package io.github.benderblog.cdripshare.ffmpeg

import io.github.benderblog.cdripshare.model.AudioItem
import io.github.benderblog.cdripshare.util.TempFileManager
import java.io.File

class AudioConcat(private val runner: FFmpegRunner) {

    suspend fun concat(
        audioFiles: List<AudioItem>,
        outputFile: File,
        totalDurationUs: Long,
        onProgress: (Float) -> Unit,
        onLog: (String) -> Unit,
    ): File {
        val concatList = TempFileManager.createConcatList()
        concatList.toFile().writeText(
            audioFiles.joinToString("\n") { "file '${it.file.absolutePath}'" }
        )

        val args = listOf(
            "-f", "concat",
            "-safe", "0",
            "-i", concatList.toString(),
            "-c", "copy",
            "-progress", "pipe:1",
            "-y",
            outputFile.absolutePath
        )

        onLog("开始拼接音频 (${audioFiles.size} 个文件)")

        val exitCode = runner.execute(args, onProgress = { value ->
            onProgress(value * 0.3f)
        }, onLog = onLog)

        if (exitCode != 0) {
            throw RuntimeException("音频拼接失败，FFmpeg 退出码: $exitCode")
        }

        onLog("音频拼接完成: ${outputFile.absolutePath}")
        return outputFile
    }
}
