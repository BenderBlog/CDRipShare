package io.github.benderblog.cdripshare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.benderblog.cdripshare.model.AudioOutputMode
import io.github.benderblog.cdripshare.model.BackgroundMode
import io.github.benderblog.cdripshare.model.Phase
import io.github.benderblog.cdripshare.model.VideoCodec
import io.github.benderblog.cdripshare.viewmodel.MainViewModel
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

@Composable
fun App(viewModel: MainViewModel) {
    val state = viewModel.appState.value
    val isWorking = state.phase == Phase.Working

    MaterialTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Row 1: 音频列表 (70%) + 图片预览 (30%) ──
                // BoxWithConstraints: 让图片的高度约束传递给列表，避免列表无限撑高
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    // 右侧需要容纳：标题 + 200dp原图 + 文件名 + 间距 + 200dp封面(16:9≈112dp)
                    val minRightHeight = 200.dp + 112.dp + 80.dp  // ≈392dp
                    val rowHeight = minRightHeight.coerceAtLeast((maxWidth * 0.3f).coerceAtMost(300.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().height(rowHeight),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AudioListPanel(
                            audioFiles = viewModel.audioFiles,
                            onMove = viewModel::moveAudioFile,
                            onRemove = viewModel::removeAudioFile,
                            onAdd = { pickAudioFiles(viewModel) },
                            onReset = viewModel::resetAudioFiles,
                            enabled = !isWorking,
                            modifier = Modifier.weight(0.7f).fillMaxHeight()
                        )
                        VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp))
                        ImagePickerPanel(
                            imageFile = viewModel.imageFile.value,
                            coverPreview = viewModel.coverPreview.value,
                            onSelect = { pickImageFile(viewModel) },
                            enabled = !isWorking,
                            bgMode = viewModel.bgMode.value,
                            onBgModeChange = remember { { viewModel.bgMode.value = it } },
                            modifier = Modifier.weight(0.3f).fillMaxHeight()
                        )
                    }
                }

                HorizontalDivider()

                // ── Row 2: 选项行 ──
                OptionsRow(viewModel, isWorking)

                HorizontalDivider()

                // ── Row 3: 进度与控制 ──
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

                if (state.phase == Phase.Idle && state.outputFile != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { Desktop.getDesktop().open(state.outputFile!!) }) {
                            Text("打开文件")
                        }
                        OutlinedButton(onClick = { Desktop.getDesktop().open(state.outputFile!!.parentFile) }) {
                            Text("打开所在文件夹")
                        }
                    }
                }

                // ── Row 4: 日志 (weight=1f 填充剩余) ──
                LogPanel(
                    logs = viewModel.logs,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OptionsRow(viewModel: MainViewModel, isWorking: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // CUE
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text("CUE:", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Text(
                viewModel.cueFile.value?.name ?: "未选择",
                style = MaterialTheme.typography.bodySmall,
                color = if (viewModel.cueFile.value != null)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (viewModel.cueFile.value != null) {
                IconButton(onClick = viewModel::clearCueFile, enabled = !isWorking, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "清除", modifier = Modifier.size(14.dp))
                }
            }
            TextButton(onClick = { pickCueFile(viewModel) }, enabled = !isWorking) {
                Text("选择", style = MaterialTheme.typography.labelMedium)
            }
        }

        // 视频编码
        EnumSelector(
            label = "视频",
            selected = viewModel.videoCodec.value,
            entries = VideoCodec.entries,
            labelOf = { it.label },
            onSelect = { viewModel.videoCodec.value = it },
            enabled = !isWorking,
            modifier = Modifier.weight(1f)
        )

        // 音频输出
        EnumSelector(
            label = "音频",
            selected = viewModel.audioOutputMode.value,
            entries = AudioOutputMode.entries,
            labelOf = { it.label },
            onSelect = { viewModel.audioOutputMode.value = it },
            enabled = !isWorking,
            modifier = Modifier.weight(1f)
        )

    }
}

@Composable
private fun <T : Enum<T>> EnumSelector(
    label: String,
    selected: T,
    entries: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label:", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(labelOf(selected), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(labelOf(entry)) },
                        onClick = { onSelect(entry); expanded = false }
                    )
                }
            }
        }
    }
}

// ── 原生文件选择器 ──

private fun nativeFileDialog(
    title: String,
    mode: Int,
    directory: String? = null,
    filter: FilenameFilter? = null,
): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    directory?.let { dialog.directory = it }
    filter?.let { dialog.filenameFilter = it }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return File(dir, file)
}

private fun nativeMultiFileDialog(title: String, filter: FilenameFilter? = null): List<File> {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isMultipleMode = true
    filter?.let { dialog.filenameFilter = it }
    dialog.isVisible = true
    return dialog.files?.toList() ?: emptyList()
}

private val audioFilter = FilenameFilter { _, name ->
    name.substringAfterLast('.', "").lowercase() in listOf("flac", "alac", "ape", "aac", "mp3", "m4a")
}
private val imageFilter = FilenameFilter { _, name ->
    name.substringAfterLast('.', "").lowercase() in listOf("jpg", "jpeg", "png", "bmp", "webp")
}
private val cueFilter = FilenameFilter { _, name -> name.endsWith(".cue", ignoreCase = true) }
private val mkvFilter = FilenameFilter { _, name -> name.endsWith(".mkv", ignoreCase = true) }

private fun pickAudioFiles(viewModel: MainViewModel) {
    nativeMultiFileDialog("选择音频文件", audioFilter).forEach { viewModel.addAudioFile(it) }
}

private fun pickImageFile(viewModel: MainViewModel) {
    nativeFileDialog("选择封面图片", FileDialog.LOAD, filter = imageFilter)?.let { viewModel.setImageFile(it) }
}

private fun pickOutputDir(viewModel: MainViewModel) {
    val dialog = FileDialog(null as Frame?, "选择输出目录", FileDialog.LOAD)
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    dialog.isVisible = true
    System.setProperty("apple.awt.fileDialogForDirectories", "false")
    val file = dialog.file ?: return
    val dir = dialog.directory ?: return
    viewModel.setOutputDir(File(dir, file))
}

private fun pickCueFile(viewModel: MainViewModel) {
    nativeFileDialog("选择 CUE 文件", FileDialog.LOAD, filter = cueFilter)?.let { viewModel.setCueFile(it) }
}

private fun startSynthesis(viewModel: MainViewModel) {
    val outputDir = viewModel.outputDir.value
    val outputFile = if (outputDir != null) {
        File(outputDir, "output.mkv")
    } else {
        nativeFileDialog("保存视频", FileDialog.SAVE, filter = mkvFilter)?.let {
            if (it.extension == "mkv") it else File(it.parentFile, "${it.name}.mkv")
        } ?: return
    }
    viewModel.startSynthesis(outputFile)
}
