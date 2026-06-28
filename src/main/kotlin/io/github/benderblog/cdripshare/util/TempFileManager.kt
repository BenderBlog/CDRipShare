package io.github.benderblog.cdripshare.util

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object TempFileManager {
    private var tempDir: Path? = null

    private fun ensureDir(): Path {
        tempDir?.let { return it }
        val dir = Files.createTempDirectory("videoaudio-")
        tempDir = dir
        Runtime.getRuntime().addShutdownHook(Thread { cleanup() })
        return dir
    }

    fun createConcatList(): Path = ensureDir().resolve("concat.txt")

    fun createMergedAudio(extension: String = "flac"): Path =
        ensureDir().resolve("merged_audio.$extension")

    fun cleanup() {
        tempDir?.let { dir ->
            dir.toFile().deleteRecursively()
            tempDir = null
        }
    }
}
