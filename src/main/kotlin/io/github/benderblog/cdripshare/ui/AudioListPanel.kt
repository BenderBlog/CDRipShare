package io.github.benderblog.cdripshare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.benderblog.cdripshare.model.AudioItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun AudioListPanel(
    audioFiles: List<AudioItem>,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onReset: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        // 工具栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "音频文件（${audioFiles.size}）",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAdd, enabled = enabled) { Text("添加") }
            TextButton(onClick = onReset, enabled = enabled && audioFiles.isNotEmpty()) { Text("重置") }
        }

        if (audioFiles.isEmpty()) {
            Text(
                "尚未添加音频文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // 表头
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                    Text("文件名", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text("编码", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp))
                    Text("时长", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp))
                    Spacer(Modifier.width(36.dp))
                }
            }

            // 表格内容
            val listState = rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                onMove(from.index, to.index)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(audioFiles, key = { _, item -> item.file.absolutePath }) { index, item ->
                    ReorderableItem(reorderableState, key = item.file.absolutePath) { isDragging ->
                        AudioItemRow(
                            index = index,
                            item = item,
                            onRemove = { onRemove(index) },
                            enabled = enabled,
                            isDragging = isDragging,
                            reorderableModifier = Modifier.draggableHandle()
                        )
                    }
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
    isDragging: Boolean,
    reorderableModifier: Modifier = Modifier,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isDragging)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(reorderableModifier)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(28.dp)
            )
            Text(
                item.displayName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                item.codec.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp)
            )
            Text(
                formatDuration(item.durationSec),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp)
            )
            IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "删除", modifier = Modifier.size(16.dp))
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
