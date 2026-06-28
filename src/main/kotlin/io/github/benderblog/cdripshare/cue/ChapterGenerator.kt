package io.github.benderblog.cdripshare.cue

import io.github.benderblog.cdripshare.model.AudioItem
import java.io.File

object ChapterGenerator {

    /**
     * Generate FFMetadata chapter file from CUE tracks and audio durations.
     * If no CUE file is provided, auto-generates chapters from audio file list.
     */
    fun generate(
        cueSheet: CueSheet?,
        audioFiles: List<AudioItem>,
        outputFile: File,
    ) {
        val sb = StringBuilder()
        sb.appendLine(";FFMETADATA1")

        if (cueSheet != null && cueSheet.tracks.isNotEmpty()) {
            // Use CUE track info
            for (i in cueSheet.tracks.indices) {
                val track = cueSheet.tracks[i]
                val startTime = track.index01Ms
                val endTime = if (i + 1 < cueSheet.tracks.size) {
                    cueSheet.tracks[i + 1].index01Ms
                } else {
                    // Last track: use sum of audio durations
                    (audioFiles.sumOf { it.durationSec } * 1000).toLong()
                }
                sb.appendLine("[CHAPTER]")
                sb.appendLine("TIMEBASE=1/1000")
                sb.appendLine("START=$startTime")
                sb.appendLine("END=$endTime")
                val artist = track.performer ?: cueSheet.performer ?: ""
                val title = track.title
                if (artist.isNotEmpty()) {
                    sb.appendLine("title=$artist - $title")
                } else {
                    sb.appendLine("title=$title")
                }
                sb.appendLine()
            }
        } else {
            // Auto-generate chapters from audio files
            var offset = 0L
            for ((index, audio) in audioFiles.withIndex()) {
                val durationMs = (audio.durationSec * 1000).toLong()
                sb.appendLine("[CHAPTER]")
                sb.appendLine("TIMEBASE=1/1000")
                sb.appendLine("START=$offset")
                sb.appendLine("END=${offset + durationMs}")
                sb.appendLine("title=${audio.displayName}")
                sb.appendLine()
                offset += durationMs
            }
        }

        outputFile.writeText(sb.toString())
    }
}
