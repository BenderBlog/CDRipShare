# 图片和音频转成视频 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Compose Desktop GUI tool that takes multiple same-codec audio files and one image, then produces an MKV video with H.265 still-image video stream and original audio.

**Architecture:** MVVM pattern — MainViewModel holds all state via Compose mutableStateOf, FFmpegRunner wraps ProcessBuilder calls to bundled FFmpeg binary, two-phase pipeline (audio concat → video mux).

**Tech Stack:** Kotlin, Compose Multiplatform Desktop, kotlinx-coroutines, FFmpeg 8.x binary, Gradle (Kotlin DSL)

---

## File Structure

```
图片和音频转成视频/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── src/main/kotlin/com/benderblog/videoaudio/
│   ├── Main.kt
│   ├── model/
│   │   ├── AppState.kt
│   │   └── AudioItem.kt
│   ├── ffmpeg/
│   │   ├── FFmpegLocator.kt
│   │   ├── FFmpegRunner.kt
│   │   ├── AudioProbe.kt
│   │   ├── AudioConcat.kt
│   │   └── VideoMuxer.kt
│   ├── util/
│   │   └── TempFileManager.kt
│   ├── viewmodel/
│   │   └── MainViewModel.kt
│   └── ui/
│       ├── App.kt
│       ├── AudioListPanel.kt
│       ├── ImagePickerPanel.kt
│       ├── ControlPanel.kt
│       └── LogPanel.kt
└── src/main/resources/ffmpeg/
    └── .gitkeep
```

---

### Task 1: Gradle Project Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
rootProject.name = "ImageAudioToVideo"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(":app")
```

- [ ] **Step 2: Create gradle/libs.versions.toml**

```toml
[versions]
kotlin = "2.1.0"
compose = "1.7.3"
coroutines = "1.9.0"

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-swing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "coroutines" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
compose = { id = "org.jetbrains.compose", version.ref = "compose" }
```

- [ ] **Step 3: Create build.gradle.kts**

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.benderblog.videoaudio.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "ImageAudioToVideo"
            packageVersion = "1.0.0"
        }
    }
}

kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 4: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2g
kotlin.code.style=official
```

- [ ] **Step 5: Create project directories**

```bash
mkdir -p src/main/kotlin/com/benderblog/videoaudio/{model,ffmpeg,util,viewmodel,ui}
mkdir -p src/main/resources/ffmpeg
touch src/main/resources/ffmpeg/.gitkeep
```

- [ ] **Step 6: Verify build**

```bash
./gradlew :app:dependencies --configuration compileClasspath
```

Expected: BUILD SUCCESSFUL with compose and coroutines dependencies resolved.

- [ ] **Step 7: Commit**

```bash
git add .
git commit -m "chore: scaffold Gradle project with Compose Desktop"
```

---

### Task 2: Data Models (AppState + AudioItem)

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/model/AudioItem.kt`
- Create: `src/main/kotlin/com/benderblog/videoaudio/model/AppState.kt`

- [ ] **Step 1: Create AudioItem.kt**

```kotlin
package com.benderblog.videoaudio.model

import java.io.File

data class AudioItem(
    val file: File,
    val codec: String,
    val durationSec: Double,
    val displayName: String = file.name,
)
```

- [ ] **Step 2: Create AppState.kt**

```kotlin
package com.benderblog.videoaudio.model

import java.io.File

enum class Phase { Idle, Working, Error }

data class AppState(
    val phase: Phase = Phase.Idle,
    val progress: Float = 0f,
    val phaseLabel: String = "",
    val errorMessage: String? = null,
    val errorDetail: String? = null,
    val outputFile: File? = null,
)
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/model/
git commit -m "feat: add AppState and AudioItem data models"
```

---

### Task 3: TempFileManager

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/util/TempFileManager.kt`

- [ ] **Step 1: Create TempFileManager.kt**

```kotlin
package com.benderblog.videoaudio.util

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
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/util/
git commit -m "feat: add TempFileManager for temp file lifecycle"
```

---

### Task 4: FFmpegLocator

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/ffmpeg/FFmpegLocator.kt`

- [ ] **Step 1: Create FFmpegLocator.kt**

```kotlin
package com.benderblog.videoaudio.ffmpeg

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
            "FFmpeg not found. Place ffmpeg binary in resources/ffmpeg/<os>-<arch>/ or add to PATH."
        )
    }

    private fun extractFromResources(name: String): Path? {
        val os = detectOs() ?: return null
        val arch = detectArch() ?: return null
        val resourcePath = "/ffmpeg/$os-$arch/$name${if (os == "win") ".exe" else ""}"
        val stream = javaClass.getResourceAsStream(resourcePath) ?: return null

        val tempDir = Files.createTempDirectory("ffmpeg-extract-")
        val target = tempDir.resolve("$name${if (os == "win") ".exe" else ""}")
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
            val candidate = Path.of(dir, "$name$ext")
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
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/ffmpeg/FFmpegLocator.kt
git commit -m "feat: add FFmpegLocator with resource extraction and PATH fallback"
```

---

### Task 5: FFmpegRunner

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/ffmpeg/FFmpegRunner.kt`

- [ ] **Step 1: Create FFmpegRunner.kt**

```kotlin
package com.benderblog.videoaudio.ffmpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

class FFmpegRunner(private val ffmpegPath: Path, private val ffprobePath: Path = Path.of(ffprobePath.toString())) {

    suspend fun execute(
        args: List<String>,
        onProgress: (Float) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        val cmd = listOf(ffmpegPath.toString()) + args
        onLog("执行: ${cmd.joinToString(" ")}")

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(false)
            .start()

        val stderrThread = Thread {
            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    if (coroutineContext.isActive) {
                        onLog("[stderr] $line")
                    }
                }
            }
        }
        stderrThread.isDaemon = true
        stderrThread.start()

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lineSequence().forEach { line ->
                if (!coroutineContext.isActive) return@forEach
                parseProgressLine(line)?.let { (key, value) ->
                    when (key) {
                        "out_time_us" -> {
                            // Progress is computed externally via totalDuration
                            // This callback reports raw microseconds
                        }
                        "progress" -> {
                            if (value == "end") onProgress(1f)
                        }
                    }
                }
            }
        }

        stderrThread.join(5000)
        process.waitFor()
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
            val ffprobePath = ffprobePath.toString()
            val process = ProcessBuilder(
                ffprobePath,
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
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/ffmpeg/FFmpegRunner.kt
git commit -m "feat: add FFmpegRunner with execute, probeDuration, probeCodec"
```

---

### Task 6: AudioConcat

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/ffmpeg/AudioConcat.kt`

- [ ] **Step 1: Create AudioConcat.kt**

```kotlin
package com.benderblog.videoaudio.ffmpeg

import com.benderblog.videoaudio.model.AudioItem
import com.benderblog.videoaudio.util.TempFileManager
import java.io.File

class AudioConcat(private val runner: FFmpegRunner) {

    suspend fun concat(
        audioFiles: List<AudioItem>,
        totalDurationUs: Long,
        onProgress: (Float) -> Unit,
        onLog: (String) -> Unit,
    ): File {
        val concatList = TempFileManager.createConcatList()
        concatList.toFile().writeText(
            audioFiles.joinToString("\n") { "file '${it.file.absolutePath}'" }
        )

        val extension = audioFiles.first().file.extension.ifEmpty { "flac" }
        val outputFile = TempFileManager.createMergedAudio(extension).toFile()

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

        onLog("音频拼接完成")
        return outputFile
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/ffmpeg/AudioConcat.kt
git commit -m "feat: add AudioConcat for lossless audio concatenation"
```

---

### Task 7: VideoMuxer

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/ffmpeg/VideoMuxer.kt`

- [ ] **Step 1: Create VideoMuxer.kt**

```kotlin
package com.benderblog.videoaudio.ffmpeg

import java.io.File

class VideoMuxer(private val runner: FFmpegRunner) {

    suspend fun mux(
        imageFile: File,
        audioFile: File,
        outputFile: File,
        totalDurationUs: Long,
        onProgress: (Float) -> Unit,
        onLog: (String) -> Unit,
    ) {
        val args = listOf(
            "-loop", "1",
            "-i", imageFile.absolutePath,
            "-i", audioFile.absolutePath,
            "-c:v", "libx265",
            "-preset", "fast",
            "-profile:v", "mainstillpicture",
            "-c:a", "copy",
            "-shortest",
            "-progress", "pipe:1",
            "-y",
            outputFile.absolutePath
        )

        onLog("开始合成视频")

        val exitCode = runner.execute(args, onProgress = { value ->
            onProgress(0.3f + value * 0.7f)
        }, onLog = onLog)

        if (exitCode != 0) {
            throw RuntimeException("视频合成失败，FFmpeg 退出码: $exitCode")
        }

        onLog("视频合成完成: ${outputFile.absolutePath}")
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/ffmpeg/VideoMuxer.kt
git commit -m "feat: add VideoMuxer with H.265 mainstillpicture profile"
```

---

### Task 8: MainViewModel

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/viewmodel/MainViewModel.kt`

- [ ] **Step 1: Create MainViewModel.kt**

```kotlin
package com.benderblog.videoaudio.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.benderblog.videoaudio.ffmpeg.*
import com.benderblog.videoaudio.model.AppState
import com.benderblog.videoaudio.model.AudioItem
import com.benderblog.videoaudio.model.Phase
import com.benderblog.videoaudio.util.TempFileManager
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainViewModel {
    val audioFiles = mutableStateListOf<AudioItem>()
    val imageFile = mutableStateOf<File?>(null)
    val outputDir = mutableStateOf<File?>(null)
    val appState = mutableStateOf(AppState())
    val logs = mutableStateListOf<String>()

    private var workJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun addAudioFile(file: File) {
        scope.launch {
            try {
                val runner = FFmpegRunner(FFmpegLocator.resolve(), FFmpegLocator.resolveFFprobe())
                val codec = runner.probeCodec(file)
                val duration = runner.probeDuration(file)
                withContext(Dispatchers.Main) {
                    audioFiles.add(AudioItem(file = file, codec = codec, durationSec = duration))
                    log("添加音频: ${file.name} ($codec, ${formatDuration(duration)})")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log("错误: 无法读取 ${file.name} - ${e.message}")
                }
            }
        }
    }

    fun removeAudioFile(index: Int) {
        if (index in audioFiles.indices) {
            val removed = audioFiles.removeAt(index)
            log("移除音频: ${removed.displayName}")
        }
    }

    fun moveAudioFile(from: Int, to: Int) {
        if (from in audioFiles.indices && to in audioFiles.indices) {
            val item = audioFiles.removeAt(from)
            audioFiles.add(to, item)
        }
    }

    fun setImageFile(file: File) {
        imageFile.value = file
        log("设置图片: ${file.name}")
    }

    fun setOutputDir(dir: File) {
        outputDir.value = dir
        log("输出目录: ${dir.absolutePath}")
    }

    fun startSynthesis(outputFile: File) {
        if (appState.value.phase == Phase.Working) return

        if (audioFiles.isEmpty()) {
            appState.value = AppState(phase = Phase.Error, errorMessage = "请先添加音频文件")
            return
        }
        if (imageFile.value == null) {
            appState.value = AppState(phase = Phase.Error, errorMessage = "请先选择图片")
            return
        }

        workJob = scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    appState.value = AppState(phase = Phase.Working, phaseLabel = "准备中...")
                }

                val runner = FFmpegRunner(FFmpegLocator.resolve(), FFmpegLocator.resolveFFprobe())
                val audioConcat = AudioConcat(runner)
                val videoMuxer = VideoMuxer(runner)

                // Probe total duration
                val totalDurationSec = audioFiles.sumOf { it.durationSec }
                val totalDurationUs = (totalDurationSec * 1_000_000).toLong()

                withContext(Dispatchers.Main) {
                    appState.value = appState.value.copy(phaseLabel = "拼接音频...")
                    log("总时长: ${formatDuration(totalDurationSec)}")
                }

                // Phase 1: Concat audio
                val mergedAudio = audioConcat.concat(
                    audioFiles = audioFiles,
                    totalDurationUs = totalDurationUs,
                    onProgress = { progress ->
                        appState.value = appState.value.copy(progress = progress)
                    },
                    onLog = { log(it) }
                )

                // Phase 2: Mux video
                withContext(Dispatchers.Main) {
                    appState.value = appState.value.copy(
                        progress = 0.3f,
                        phaseLabel = "合成视频..."
                    )
                }

                videoMuxer.mux(
                    imageFile = imageFile.value!!,
                    audioFile = mergedAudio,
                    outputFile = outputFile,
                    totalDurationUs = totalDurationUs,
                    onProgress = { progress ->
                        appState.value = appState.value.copy(progress = progress)
                    },
                    onLog = { log(it) }
                )

                withContext(Dispatchers.Main) {
                    appState.value = AppState(
                        phase = Phase.Idle,
                        progress = 1f,
                        outputFile = outputFile
                    )
                    log("合成完成: ${outputFile.absolutePath}")
                }

            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    appState.value = AppState(
                        phase = Phase.Idle,
                        phaseLabel = "已取消"
                    )
                    log("合成已取消")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appState.value = AppState(
                        phase = Phase.Error,
                        errorMessage = e.message ?: "未知错误",
                        errorDetail = e.stackTraceToString()
                    )
                    log("错误: ${e.message}")
                }
            } finally {
                TempFileManager.cleanup()
            }
        }
    }

    fun cancelSynthesis() {
        workJob?.cancel()
    }

    fun resetToIdle() {
        appState.value = AppState()
    }

    private fun log(message: String) {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        logs.add("[$time] $message")
    }

    private fun formatDuration(seconds: Double): String {
        val totalSec = seconds.toLong()
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/viewmodel/
git commit -m "feat: add MainViewModel with full synthesis workflow"
```

---

### Task 9: UI - AudioListPanel

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/ui/AudioListPanel.kt`

- [ ] **Step 1: Create AudioListPanel.kt**

```kotlin
package com.benderblog.videoaudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.benderblog.videoaudio.model.AudioItem

@Composable
fun AudioListPanel(
    audioFiles: List<AudioItem>,
    onRemove: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "音频文件",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (audioFiles.isEmpty()) {
            Text(
                "尚未添加音频文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(audioFiles) { index, item ->
                    AudioItemRow(
                        index = index,
                        item = item,
                        onRemove = { onRemove(index) },
                        enabled = enabled
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioItemRow(
    index: Int,
    item: AudioItem,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${index + 1}.",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatDuration(item.durationSec)}  ${item.codec.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val totalSec = seconds.toLong()
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/ui/AudioListPanel.kt
git commit -m "feat: add AudioListPanel with indexed list and delete"
```

---

### Task 10: UI - ImagePickerPanel

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/ui/ImagePickerPanel.kt`

- [ ] **Step 1: Create ImagePickerPanel.kt**

```kotlin
package com.benderblog.videoaudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun ImagePickerPanel(
    imageFile: File?,
    onSelect: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "图片: ",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            imageFile?.name ?: "未选择",
            style = MaterialTheme.typography.bodyMedium,
            color = if (imageFile != null)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Button(onClick = onSelect, enabled = enabled) {
            Text("选择图片")
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/ui/ImagePickerPanel.kt
git commit -m "feat: add ImagePickerPanel"
```

---

### Task 11: UI - ControlPanel

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/ui/ControlPanel.kt`

- [ ] **Step 1: Create ControlPanel.kt**

```kotlin
package com.benderblog.videoaudio.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.benderblog.videoaudio.model.Phase

@Composable
fun ControlPanel(
    phase: Phase,
    progress: Float,
    phaseLabel: String,
    errorMessage: String?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Buttons row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (phase) {
                Phase.Idle -> {
                    Button(onClick = onStart) {
                        Text("开始合成")
                    }
                }
                Phase.Working -> {
                    OutlinedButton(onClick = onCancel) {
                        Text("取消")
                    }
                }
                Phase.Error -> {
                    Button(onClick = onRetry) {
                        Text("重试")
                    }
                    OutlinedButton(onClick = onClear) {
                        Text("清除")
                    }
                }
            }
        }

        // Progress bar
        if (phase == Phase.Working || (phase == Phase.Idle && progress > 0f)) {
            Column {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (phase == Phase.Error)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                if (phaseLabel.isNotEmpty()) {
                    Text(
                        phaseLabel,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Error display
        if (phase == Phase.Error && errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    errorMessage,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/ui/ControlPanel.kt
git commit -m "feat: add ControlPanel with progress bar and error display"
```

---

### Task 12: UI - LogPanel

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/ui/LogPanel.kt`

- [ ] **Step 1: Create LogPanel.kt**

```kotlin
package com.benderblog.videoaudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LogPanel(
    logs: List<String>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "日志",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/ui/LogPanel.kt
git commit -m "feat: add LogPanel with auto-scroll"
```

---

### Task 13: UI - App (Root Composable)

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/ui/App.kt`

- [ ] **Step 1: Create App.kt**

```kotlin
package com.benderblog.videoaudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.benderblog.videoaudio.model.Phase
import com.benderblog.videoaudio.viewmodel.MainViewModel
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: MainViewModel) {
    val state = viewModel.appState.value
    val isWorking = state.phase == Phase.Working

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("图片和音频转成视频") },
                    actions = {
                        TextButton(
                            onClick = { pickOutputDir(viewModel) },
                            enabled = !isWorking
                        ) {
                            Text(
                                viewModel.outputDir.value?.name ?: "设置输出目录"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Audio list
                AudioListPanel(
                    audioFiles = viewModel.audioFiles,
                    onRemove = viewModel::removeAudioFile,
                    enabled = !isWorking
                )

                // Add audio button
                Button(
                    onClick = { pickAudioFiles(viewModel) },
                    enabled = !isWorking
                ) {
                    Text("添加音频")
                }

                HorizontalDivider()

                // Image picker
                ImagePickerPanel(
                    imageFile = viewModel.imageFile.value,
                    onSelect = { pickImageFile(viewModel) },
                    enabled = !isWorking
                )

                HorizontalDivider()

                // Control panel
                ControlPanel(
                    phase = state.phase,
                    progress = state.progress,
                    phaseLabel = state.phaseLabel,
                    errorMessage = state.errorMessage,
                    onStart = { startSynthesis(viewModel) },
                    onCancel = viewModel::cancelSynthesis,
                    onRetry = { startSynthesis(viewModel) },
                    onClear = viewModel::resetToIdle
                )

                // Output actions (when done)
                if (state.phase == Phase.Idle && state.outputFile != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            Desktop.getDesktop().open(state.outputFile!!)
                        }) {
                            Text("打开文件")
                        }
                        OutlinedButton(onClick = {
                            Desktop.getDesktop().open(state.outputFile!!.parentFile)
                        }) {
                            Text("打开所在文件夹")
                        }
                    }
                }

                // Log panel
                LogPanel(
                    logs = viewModel.logs,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun pickAudioFiles(viewModel: MainViewModel) {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter(
            "音频文件 (FLAC, ALAC, APE, AAC, MP3)",
            "flac", "alac", "ape", "aac", "mp3", "m4a"
        )
        isMultiSelectionEnabled = true
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.forEach { viewModel.addAudioFile(it) }
    }
}

private fun pickImageFile(viewModel: MainViewModel) {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter(
            "图片文件 (JPG, PNG, BMP, WebP)",
            "jpg", "jpeg", "png", "bmp", "webp"
        )
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        viewModel.setImageFile(chooser.selectedFile)
    }
}

private fun pickOutputDir(viewModel: MainViewModel) {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        viewModel.setOutputDir(chooser.selectedFile)
    }
}

private fun startSynthesis(viewModel: MainViewModel) {
    val outputDir = viewModel.outputDir.value
    val outputFile = if (outputDir != null) {
        File(outputDir, "output.mkv")
    } else {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("MKV 视频", "mkv")
            selectedFile = File("output.mkv")
        }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.let {
                if (it.extension == "mkv") it else File(it.parentFile, "${it.name}.mkv")
            }
        } else return
    }
    viewModel.startSynthesis(outputFile)
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/ui/App.kt
git commit -m "feat: add App root composable with Scaffold layout"
```

---

### Task 14: Main.kt Entry Point

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/Main.kt`

- [ ] **Step 1: Create Main.kt**

```kotlin
package com.benderblog.videoaudio

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.benderblog.videoaudio.ui.App
import com.benderblog.videoaudio.viewmodel.MainViewModel

fun main() = application {
    val state = rememberWindowState(width = 600.dp, height = 800.dp)
    val viewModel = MainViewModel()

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "图片和音频转成视频"
    ) {
        App(viewModel)
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/benderblog/videoaudio/Main.kt
git commit -m "feat: add Main.kt entry point with Window"
```

---

### Task 15: Build and Run Verification

- [ ] **Step 1: Run the application**

```bash
./gradlew :app:run
```

Expected: Window opens showing the GUI with empty audio list, image picker, and log panel.

- [ ] **Step 2: Verify UI layout**

Check:
- AppBar with title "图片和音频转成视频" and "设置输出目录" button
- Empty audio list showing "尚未添加音频文件"
- "添加音频" button
- Image picker showing "未选择"
- "开始合成" button
- Empty log panel

- [ ] **Step 3: Commit final state**

```bash
git add .
git commit -m "chore: verify build and run"
```
