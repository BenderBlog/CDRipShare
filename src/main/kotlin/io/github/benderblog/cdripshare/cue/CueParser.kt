package io.github.benderblog.cdripshare.cue

import java.io.File

data class CueTrack(
    val number: Int,
    val title: String,
    val performer: String?,
    val index01Ms: Long,  // INDEX 01 timestamp in milliseconds
)

data class CueSheet(
    val title: String?,
    val performer: String?,
    val file: String?,
    val tracks: List<CueTrack>,
)

object CueParser {

    fun parse(file: File): CueSheet {
        val lines = file.readLines().map { it.trim() }

        var globalTitle: String? = null
        var globalPerformer: String? = null
        var globalFile: String? = null
        val tracks = mutableListOf<CueTrack>()

        var currentTrackNumber: Int? = null
        var currentTitle: String? = null
        var currentPerformer: String? = null
        var currentIndex01: Long? = null

        for (line in lines) {
            when {
                line.startsWith("TITLE ") && currentTrackNumber == null -> {
                    globalTitle = extractQuoted(line, "TITLE")
                }
                line.startsWith("PERFORMER ") && currentTrackNumber == null -> {
                    globalPerformer = extractQuoted(line, "PERFORMER")
                }
                line.startsWith("FILE ") -> {
                    globalFile = extractQuoted(line, "FILE")
                }
                line.startsWith("TRACK ") -> {
                    // Save previous track
                    currentTrackNumber?.let { num ->
                        tracks.add(CueTrack(num, currentTitle ?: "Track $num", currentPerformer, currentIndex01 ?: 0L))
                    }
                    currentTrackNumber = line.substringAfter("TRACK ").substringBefore(" ").toIntOrNull()
                    currentTitle = null
                    currentPerformer = globalPerformer
                    currentIndex01 = null
                }
                line.startsWith("TITLE ") && currentTrackNumber != null -> {
                    currentTitle = extractQuoted(line, "TITLE")
                }
                line.startsWith("PERFORMER ") && currentTrackNumber != null -> {
                    currentPerformer = extractQuoted(line, "PERFORMER")
                }
                line.startsWith("INDEX 01 ") -> {
                    currentIndex01 = parseCueTimestamp(line.substringAfter("INDEX 01 ").trim())
                }
            }
        }
        // Save last track
        currentTrackNumber?.let { num ->
            tracks.add(CueTrack(num, currentTitle ?: "Track $num", currentPerformer, currentIndex01 ?: 0L))
        }

        return CueSheet(globalTitle, globalPerformer, globalFile, tracks)
    }

    private fun extractQuoted(line: String, keyword: String): String {
        val afterKeyword = line.substringAfter("$keyword ")
        return if (afterKeyword.startsWith("\"")) {
            afterKeyword.substringAfter("\"").substringBefore("\"")
        } else {
            afterKeyword.trim()
        }
    }

    /**
     * Parse CUE timestamp format: MM:SS:FF (frames, 75 per second)
     */
    private fun parseCueTimestamp(ts: String): Long {
        val parts = ts.split(":")
        if (parts.size != 3) return 0L
        val minutes = parts[0].toLongOrNull() ?: 0L
        val seconds = parts[1].toLongOrNull() ?: 0L
        val frames = parts[2].toLongOrNull() ?: 0L
        return (minutes * 60_000) + (seconds * 1000) + (frames * 1000 / 75)
    }
}
