package io.github.benderblog.cdripshare.ffmpeg

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

object FFmpegLocator {

    fun resolve(): Path = resolveBinary("ffmpeg")

    fun resolveFFprobe(): Path = resolveBinary("ffprobe")

    private fun resolveBinary(name: String): Path {
        val fromResources = extractFromResources(name)
        if (fromResources != null) return fromResources

        val fromPath = findOnPath(name)
        if (fromPath != null) return fromPath

        throw IllegalStateException(
            "$name not found. Place $name binary in resources/ffmpeg/<os>-<arch>/ or add to PATH."
        )
    }

    private fun extractFromResources(name: String): Path? {
        val os = detectOs() ?: return null
        val arch = detectArch() ?: return null
        val ext = if (os == "win") ".exe" else ""
        val resourcePath = "/ffmpeg/$os-$arch/$name$ext"
        val stream = javaClass.getResourceAsStream(resourcePath) ?: return null

        val tempDir = Files.createTempDirectory("ffmpeg-extract-")
        val target = tempDir.resolve("$name$ext")
        stream.use { input ->
            Files.newOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }

        if (os != "win") {
            Files.setPosixFilePermissions(
                target,
                PosixFilePermissions.fromString("rwxr-xr-x")
            )
        }

        target.toFile().deleteOnExit()
        tempDir.toFile().deleteOnExit()
        return target
    }

    private fun findOnPath(name: String): Path? {
        val ext = if (detectOs() == "win") ".exe" else ""
        val pathDirs = System.getenv("PATH")?.split(":") ?: return null
        for (dir in pathDirs) {
            val candidate = java.nio.file.Paths.get(dir, "$name$ext")
            if (Files.isExecutable(candidate)) return candidate
        }
        return null
    }

    private fun detectOs(): String? {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> "macos"
            os.contains("win") -> "win"
            os.contains("linux") -> "linux"
            else -> null
        }
    }

    private fun detectArch(): String? {
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
            else -> null
        }
    }
}
