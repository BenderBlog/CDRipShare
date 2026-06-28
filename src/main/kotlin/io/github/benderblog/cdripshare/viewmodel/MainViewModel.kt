package io.github.benderblog.cdripshare.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.runtime.mutableStateOf
import io.github.benderblog.cdripshare.ffmpeg.*
import io.github.benderblog.cdripshare.cue.CueParser
import io.github.benderblog.cdripshare.cue.ChapterGenerator
import io.github.benderblog.cdripshare.model.AppState
import io.github.benderblog.cdripshare.model.AudioItem
import io.github.benderblog.cdripshare.model.AudioOutputMode
import io.github.benderblog.cdripshare.model.BackgroundMode
import io.github.benderblog.cdripshare.model.Phase
import io.github.benderblog.cdripshare.model.VideoCodec
import io.github.benderblog.cdripshare.util.TempFileManager
import io.github.benderblog.cdripshare.util.CoverImageGenerator
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainViewModel {
    val audioFiles = mutableStateListOf<AudioItem>()
    val imageFile = mutableStateOf<File?>(null)
    val coverPreview = mutableStateOf<ImageBitmap?>(null)
    val outputDir = mutableStateOf<File?>(null)
    val appState = mutableStateOf(AppState())
    val cueFile = mutableStateOf<File?>(null)
    val audioOutputMode = mutableStateOf(AudioOutputMode.Passthrough)
    val videoCodec = mutableStateOf(VideoCodec.H265)
    val bgMode = mutableStateOf(BackgroundMode.Auto)
    val logs = mutableStateListOf<String>()

    private var workJob: Job? = null
    private var activeRunner: FFmpegRunner? = null
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

    fun resetAudioFiles() {
        audioFiles.clear()
        log("已重置音频列表")
    }

    fun setCueFile(file: File) {
        cueFile.value = file
        log("设置 CUE: ${file.name}")
    }

    fun clearCueFile() {
        cueFile.value = null
    }

    fun setImageFile(file: File) {
        imageFile.value = file
        coverPreview.value = null  // 立即清空旧预览
        log("设置图片: ${file.name}")
        generateCoverPreview(file)
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
            val runner = FFmpegRunner(FFmpegLocator.resolve(), FFmpegLocator.resolveFFprobe())
            activeRunner = runner
            try {
                withContext(Dispatchers.Main) {
                    appState.value = AppState(phase = Phase.Working, phaseLabel = "准备中...")
                }

                val videoMuxer = VideoMuxer(runner)
                val mode = audioOutputMode.value
                val codec = videoCodec.value

                withContext(Dispatchers.Main) {
                    appState.value = appState.value.copy(phaseLabel = "合成视频...")
                    log("总时长: ${formatDuration(audioFiles.sumOf { it.durationSec })}")
                    log("音频输出: ${mode.label}, 视频编码: ${codec.label}")
                }

                val chapterFile = File(outputFile.parentFile, "chapters.ffmeta")
                val cueSheet = cueFile.value?.let {
                    try {
                        val parsed = CueParser.parse(it)
                        log("解析 CUE: ${parsed.tracks.size} 个音轨")
                        parsed
                    } catch (e: Exception) {
                        log("CUE 解析失败: ${e.message}")
                        null
                    }
                }
                ChapterGenerator.generate(cueSheet, audioFiles, chapterFile)

                // 生成封面图
                val coverFile = File(outputFile.parentFile, "cover.png")
                CoverImageGenerator.generate(imageFile.value!!, coverFile, bgMode.value)
                log("封面图已生成: ${coverFile.absolutePath}")

                videoMuxer.mux(
                    audioFiles = audioFiles,
                    imageFile = coverFile,
                    chapterFile = chapterFile,
                    outputFile = outputFile,
                    audioOutputMode = mode,
                    videoCodec = codec,
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
                withContext(NonCancellable) {
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
                activeRunner = null
                TempFileManager.cleanup()
            }
        }
    }

    fun cancelSynthesis() {
        activeRunner?.destroyCurrentProcess()
        workJob?.cancel()
        appState.value = AppState()
    }


    fun onBgModeChanged() {
        imageFile.value?.let { generateCoverPreview(it) }
    }

    fun resetToIdle() {
        appState.value = AppState()
    }

    private fun generateCoverPreview(file: File) {
        scope.launch(Dispatchers.IO) {
            try {
                val tempCover = File.createTempFile("cover-preview-", ".png")
                CoverImageGenerator.generate(file, tempCover, bgMode.value)
                val bitmap = org.jetbrains.skia.Image.makeFromEncoded(tempCover.readBytes()).toComposeImageBitmap()
                withContext(Dispatchers.Main) {
                    coverPreview.value = bitmap
                }
                tempCover.deleteOnExit()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log("封面预览生成失败: ${e.message}")
                    coverPreview.value = null
                }
            }
        }
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
