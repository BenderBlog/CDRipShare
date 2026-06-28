package io.github.benderblog.cdripshare.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

import io.github.benderblog.cdripshare.model.BackgroundMode

@Composable
fun ImagePickerPanel(
    imageFile: File?,
    coverPreview: ImageBitmap?,
    onSelect: () -> Unit,
    enabled: Boolean,
    bgMode: BackgroundMode,
    onBgModeChange: (BackgroundMode) -> Unit,
    customColorHex: String,
    onCustomColorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(imageFile?.absolutePath) {
        imageFile?.takeIf { it.exists() }?.let {
            try {
                org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    var showColorDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "封面图片",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 正方形预览区
        Surface(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { onSelect() },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "封面预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "点击选择封面",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 文件名 + 分辨率
        Spacer(Modifier.height(6.dp))
        Text(
            imageFile?.name ?: "未选择",
            style = MaterialTheme.typography.bodySmall,
            color = if (imageFile != null)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (bitmap != null) {
            Text(
                "${bitmap.width} × ${bitmap.height}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 封面预览（1920×1080 生成结果）
        if (coverPreview != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "封面预览",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(Modifier.height(2.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Image(
                    bitmap = coverPreview,
                    contentDescription = "封面预览 (1920×1080)",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // 背景色选择
        Spacer(Modifier.height(8.dp))
        BgModeSelector(
            bgMode = bgMode,
            onBgModeChange = { mode ->
                if (mode == BackgroundMode.Custom) {
                    showColorDialog = true
                } else {
                    onBgModeChange(mode)
                }
            },
            enabled = enabled
        )

        // 自定义模式：当前色值预览
        if (bgMode == BackgroundMode.Custom) {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "当前: #${customColorHex.removePrefix("#")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)),
                    color = try {
                        val hex = customColorHex.removePrefix("#")
                        val c = hex.toInt(16)
                        androidx.compose.ui.graphics.Color(c or (0xFF shl 24))
                    } catch (_: Exception) {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {}
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { showColorDialog = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("选择颜色…", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    // 自定义颜色弹窗
    if (showColorDialog) {
        ColorPickerDialog(
            initialHex = customColorHex.removePrefix("#"),
            onConfirm = { hex ->
                onBgModeChange(BackgroundMode.Custom)
                onCustomColorChange(hex)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false }
        )
    }
}

@Composable
private fun ColorPickerDialog(
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var hex by remember { mutableStateOf(initialHex) }
    val previewColor = try {
        val c = hex.toInt(16)
        androidx.compose.ui.graphics.Color(c or (0xFF shl 24))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.surfaceVariant
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义背景色", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 色块预览
                Surface(
                    modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(8.dp)),
                    color = previewColor,
                    shape = RoundedCornerShape(8.dp)
                ) {}

                // 十六进制输入
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "#",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    BasicTextField(
                        value = hex,
                        onValueChange = { hex = it.filter { c -> c in "0123456789ABCDEFabcdef" }.take(6) },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }

                // 预设色块快捷选择
                Text("预设色", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "3A3D60" to "深蓝紫",
                        "1F2D48" to "藏蓝",
                        "1A1F2E" to "GitHub",
                        "3C3C3C" to "深灰",
                        "2A3A2E" to "墨绿",
                        "3C2E24" to "深棕",
                        "222222" to "经典黑"
                    ).forEach { (h, label) ->
                        val c = try {
                            val v = h.toInt(16)
                            androidx.compose.ui.graphics.Color(v or (0xFF shl 24))
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                        Surface(
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable { hex = h },
                            color = c,
                            shape = RoundedCornerShape(6.dp)
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hex) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun BgModeSelector(
    bgMode: BackgroundMode,
    onBgModeChange: (BackgroundMode) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "背景:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    bgMode.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                BackgroundMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                        onClick = { onBgModeChange(mode); expanded = false }
                    )
                }
            }
        }
    }
}
