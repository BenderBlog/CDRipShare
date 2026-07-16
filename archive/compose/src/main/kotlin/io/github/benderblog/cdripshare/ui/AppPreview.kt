package io.github.benderblog.cdripshare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.benderblog.cdripshare.model.*
import io.github.benderblog.cdripshare.viewmodel.MainViewModel
import androidx.compose.desktop.ui.tooling.preview.Preview
import java.io.File

private fun MainViewModel.addMockAudio(count: Int) {
    val codecs = listOf("flac", "aac", "mp3", "flac", "ape", "flac")
    for (i in 1..count) {
        val codec = codecs[(i - 1) % codecs.size]
        val name = "%02d-Track%02d.%s".format(i, i, if (codec == "aac") "m4a" else codec)
        audioFiles.add(
            AudioItem(
                file = File("/tmp/$name"),
                codec = codec,
                durationSec = 180.0 + (i * 17) % 120,
                displayName = name
            )
        )
    }
}

private fun MainViewModel.addMockLogs() {
    logs.addAll(listOf(
        "[12:00:00] 添加音频: 01-Track01.flac (flac, 3:12)",
        "[12:00:01] 添加音频: 02-Track02.m4a (aac, 4:05)",
        "[12:00:02] 设置图片: cover.jpg",
        "[12:00:03] 输出目录: /Users/demo/Music",
    ))
}

@Preview
@Composable
fun AppPreviewIdle() {
    val vm = remember {
        MainViewModel().apply {
            addMockAudio(8)
            imageFile.value = File("/tmp/cover.jpg")
            outputDir.value = File("/Users/demo/Music")
            addMockLogs()
        }
    }
    Box(Modifier.size(500.dp, 700.dp)) {
        App(viewModel = vm)
    }
}

@Preview
@Composable
fun AppPreviewWorking() {
    val vm = remember {
        MainViewModel().apply {
            addMockAudio(3)
            imageFile.value = File("/tmp/cover.jpg")
            outputDir.value = File("/Users/demo/Music")
            addMockLogs()
            logs.add("[12:00:04] 音频输出: 不升频（保持原样）, 视频编码: H.265 (HEVC)")
            logs.add("[12:00:05] 总时长: 10:23")
            logs.add("[12:00:05] 开始合成视频 (3 个音频 + 图片 → 1080p)")
            appState.value = AppState(
                phase = Phase.Working,
                progress = 0.45f,
                phaseLabel = "合成视频..."
            )
        }
    }
    Box(Modifier.size(500.dp, 700.dp)) {
        App(viewModel = vm)
    }
}

@Preview
@Composable
fun AppPreviewEmpty() {
    val vm = remember { MainViewModel() }
    Box(Modifier.size(500.dp, 700.dp)) {
        App(viewModel = vm)
    }
}
