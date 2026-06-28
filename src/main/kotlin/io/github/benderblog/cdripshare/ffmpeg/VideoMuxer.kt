package io.github.benderblog.cdripshare.ffmpeg

import io.github.benderblog.cdripshare.model.AudioItem
import io.github.benderblog.cdripshare.model.AudioOutputMode
import io.github.benderblog.cdripshare.model.VideoCodec
import java.io.File

class VideoMuxer(private val runner: FFmpegRunner) {

    suspend fun mux(
        audioFiles: List<AudioItem>,
        imageFile: File,
        outputFile: File,
        chapterFile: File? = null,
        audioOutputMode: AudioOutputMode = AudioOutputMode.Passthrough,
        videoCodec: VideoCodec = VideoCodec.H265,
        onProgress: (Float) -> Unit,
        onLog: (String) -> Unit,
    ) {
        val n = audioFiles.size
        val totalDurationSec = audioFiles.sumOf { it.durationSec }
        val totalDurationUs = (totalDurationSec * 1_000_000).toLong()

        val inputs = audioFiles.map { listOf("-i", it.file.absolutePath) }.flatten() +
                listOf("-loop", "1", "-i", imageFile.absolutePath)

        // Audio concat filter
        val audioInputs = audioFiles.indices.joinToString("") { "[$it:a]" }
        val audioConcat = "${audioInputs}concat=n=$n:v=0:a=1[aconcat]"

        // Video scale+pad filter
        val videoFilter = "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2"

        // Audio output filter
        val audioFilterSuffix = when (audioOutputMode) {
            AudioOutputMode.Passthrough -> ""
            AudioOutputMode.HiRes48 -> ";[aconcat]aformat=sample_rates=48000:sample_fmts=s32[aout]"
            AudioOutputMode.HiRes96 -> ";[aconcat]aformat=sample_rates=96000:sample_fmts=s32[aout]"
            AudioOutputMode.AAC256, AudioOutputMode.AAC192 -> ""
        }
        val audioMapLabel = if (audioFilterSuffix.isEmpty()) "[aconcat]" else "[aout]"

        val filterComplex = "$audioConcat;[$n:v]$videoFilter[vout]$audioFilterSuffix"

        // Audio codec args
        val audioCodecArgs = when (audioOutputMode) {
            AudioOutputMode.Passthrough -> resolveAudioCodecArgs(audioFiles.first().codec)
            AudioOutputMode.HiRes48, AudioOutputMode.HiRes96 -> listOf("-c:a", "flac")
            AudioOutputMode.AAC256 -> listOf("-c:a", "aac", "-b:a", "256k")
            AudioOutputMode.AAC192 -> listOf("-c:a", "aac", "-b:a", "192k")
        }

        // Video codec args
        val videoCodecArgs = when (videoCodec) {
            VideoCodec.H265 -> listOf(
                "-c:v", "libx265",
                "-preset", "fast",
                "-crf", "35",
                "-x265-params", "profile=mainstillpicture",
            )
            VideoCodec.H264 -> listOf(
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "23",
                "-tune", "stillimage",
                "-profile:v", "high",
            )
        }

        val chapterArgs = if (chapterFile != null) {
            listOf("-i", chapterFile.absolutePath, "-map_metadata", "1")
        } else {
            emptyList()
        }

        val args = inputs + chapterArgs + listOf(
            "-filter_complex", filterComplex,
            "-map", "[vout]",
            "-map", audioMapLabel,
        ) + videoCodecArgs + audioCodecArgs + listOf(
            "-shortest",
            "-progress", "pipe:1",
            "-y",
            outputFile.absolutePath
        )

        onLog("开始合成视频 (${n} 个音频 + 图片 → 1080p, 视频: ${videoCodec.label}, 音频: ${audioOutputMode.label})")

        val exitCode = runner.execute(
            args,
            totalDurationUs = totalDurationUs,
            onProgress = { onProgress(it) },
            onLog = onLog
        )

        if (exitCode != 0) {
            throw RuntimeException("视频合成失败，FFmpeg 退出码: $exitCode")
        }

        onLog("视频合成完成: ${outputFile.absolutePath}")
    }

    private fun resolveAudioCodecArgs(codec: String): List<String> = when (codec.lowercase()) {
        "flac" -> listOf("-c:a", "flac")
        "alac" -> listOf("-c:a", "alac")
        "aac"  -> listOf("-c:a", "aac", "-b:a", "256k")
        "mp3"  -> listOf("-c:a", "libmp3lame", "-b:a", "320k")
        "ape"  -> listOf("-c:a", "flac")
        else   -> listOf("-c:a", "copy")
    }
}
