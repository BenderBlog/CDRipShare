package io.github.benderblog.cdripshare.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

import io.github.benderblog.cdripshare.model.BackgroundMode
import io.github.benderblog.cdripshare.model.CoverRenderSettings
import io.github.benderblog.cdripshare.util.ColorHex
import io.github.benderblog.cdripshare.util.CoverImageGenerator
import kotlin.math.roundToInt

@Composable
fun ImagePickerPanel(
    imageFile: File?,
    backgroundColorArgb: Int,
    coverRenderSettings: CoverRenderSettings,
    onEdit: () -> Unit,
    enabled: Boolean,
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

    Column(
        modifier = modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("封面图片", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))

        Surface(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onEdit() },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = "原始封面", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text("点击编辑封面", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            imageFile?.name ?: "未选择",
            style = MaterialTheme.typography.bodySmall,
            color = if (imageFile != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (bitmap != null) {
            Text("${bitmap.width} × ${bitmap.height}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        if (bitmap != null) {
            Spacer(Modifier.height(12.dp))
            Text("封面预览", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.height(2.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                LiveCoverPreview(
                    bitmap = bitmap,
                    backgroundColorArgb = backgroundColorArgb,
                    settings = coverRenderSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(if (enabled) "编辑封面" else "查看封面", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun CoverEditorContent(
    imageFile: File?,
    backgroundColorArgb: Int,
    onSelect: () -> Unit,
    enabled: Boolean,
    bgMode: BackgroundMode,
    onBgModeChange: (BackgroundMode) -> Unit,
    customColorHex: String,
    onCustomColorConfirm: (String) -> Unit,
    coverRenderSettings: CoverRenderSettings,
    onCoverRenderSettingsChanging: (CoverRenderSettings) -> Unit,
    onCoverRenderSettingsChange: (CoverRenderSettings) -> Unit,
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
    var showAdvancedShadow by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled) showColorDialog = false
    }

    Row(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("封面预览", style = MaterialTheme.typography.titleMedium)
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (bitmap != null) {
                    LiveCoverPreview(
                        bitmap = bitmap,
                        backgroundColorArgb = backgroundColorArgb,
                        settings = coverRenderSettings,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text("选择封面后显示实时预览", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }

        }

        Column(
            modifier = Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onSelect, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (imageFile == null) "选择封面" else "更换封面")
            }

            BgModeSelector(
                bgMode = bgMode,
                onBgModeChange = { mode ->
                    if (mode == BackgroundMode.Custom) showColorDialog = true
                    else onBgModeChange(mode)
                },
                enabled = enabled
            )

            if (bgMode == BackgroundMode.Custom) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("当前: #${customColorHex.removePrefix("#")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Surface(modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)), color = parseHexColor(customColorHex)) {}
                }
                OutlinedButton(
                    onClick = { showColorDialog = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("选择颜色")
                }
            }

            HorizontalDivider()
            Text("构图", style = MaterialTheme.typography.titleSmall)
            CoverSettingSlider(
                label = "图片高度",
                value = coverRenderSettings.imageHeight,
                valueRange = CoverRenderSettings.MIN_IMAGE_HEIGHT..CoverRenderSettings.MAX_IMAGE_HEIGHT,
                suffix = "px",
                enabled = enabled,
                onValueChanging = { value ->
                    onCoverRenderSettingsChanging(coverRenderSettings.withImageHeight(value))
                },
                onValueCommitted = { value ->
                    onCoverRenderSettingsChange(coverRenderSettings.withImageHeight(value))
                }
            )
            CoverSettingSlider(
                label = "圆角",
                value = coverRenderSettings.cornerRadius,
                valueRange = CoverRenderSettings.MIN_CORNER_RADIUS..CoverRenderSettings.MAX_CORNER_RADIUS,
                suffix = "px",
                enabled = enabled,
                onValueChanging = { value ->
                    onCoverRenderSettingsChanging(coverRenderSettings.withCornerRadius(value))
                },
                onValueCommitted = { value ->
                    onCoverRenderSettingsChange(coverRenderSettings.withCornerRadius(value))
                }
            )

            HorizontalDivider()
            Text("阴影", style = MaterialTheme.typography.titleSmall)
            CoverSettingSlider(
                label = "强度",
                value = coverRenderSettings.shadowStrength * 100f,
                valueRange = 0f..100f,
                suffix = "%",
                enabled = enabled,
                onValueChanging = { value ->
                    onCoverRenderSettingsChanging(coverRenderSettings.withShadowStrength(value / 100f))
                },
                onValueCommitted = { value ->
                    onCoverRenderSettingsChange(coverRenderSettings.withShadowStrength(value / 100f))
                }
            )
            if (coverRenderSettings.usesAdvancedShadow) {
                Text("高级阴影已自定义", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(
                onClick = { showAdvancedShadow = !showAdvancedShadow },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(if (showAdvancedShadow) "收起高级阴影" else "高级阴影", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            if (showAdvancedShadow) {
                CoverSettingSlider(
                    label = "模糊",
                    value = coverRenderSettings.shadowBlur,
                    valueRange = CoverRenderSettings.MIN_SHADOW_BLUR..CoverRenderSettings.MAX_SHADOW_BLUR,
                    suffix = "px",
                    enabled = enabled,
                    onValueChanging = { value ->
                        onCoverRenderSettingsChanging(coverRenderSettings.withShadowBlur(value))
                    },
                    onValueCommitted = { value ->
                        onCoverRenderSettingsChange(coverRenderSettings.withShadowBlur(value))
                    }
                )
                CoverSettingSlider(
                    label = "下移",
                    value = coverRenderSettings.shadowOffsetY,
                    valueRange = CoverRenderSettings.MIN_SHADOW_OFFSET_Y..CoverRenderSettings.MAX_SHADOW_OFFSET_Y,
                    suffix = "px",
                    enabled = enabled,
                    onValueChanging = { value ->
                        onCoverRenderSettingsChanging(coverRenderSettings.withShadowOffsetY(value))
                    },
                    onValueCommitted = { value ->
                        onCoverRenderSettingsChange(coverRenderSettings.withShadowOffsetY(value))
                    }
                )
                CoverSettingSlider(
                    label = "透明度",
                    value = coverRenderSettings.shadowAlpha * 100f,
                    valueRange = 0f..100f,
                    suffix = "%",
                    enabled = enabled,
                    onValueChanging = { value ->
                        onCoverRenderSettingsChanging(coverRenderSettings.withShadowAlpha(value / 100f))
                    },
                    onValueCommitted = { value ->
                        onCoverRenderSettingsChange(coverRenderSettings.withShadowAlpha(value / 100f))
                    }
                )
            }

            if (!enabled) {
                Text("合成中只能查看封面预览", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("原始图片", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    if (bitmap != null) {
                        Image(bitmap = bitmap, contentDescription = "原始封面", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("未选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(imageFile?.name ?: "未选择封面图片", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (bitmap != null) {
                        Text("${bitmap.width} × ${bitmap.height}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showColorDialog && enabled) {
        PaletteColorPickerDialog(
            initialHex = customColorHex.removePrefix("#"),
            onConfirm = { hex ->
                onCustomColorConfirm(hex)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false }
        )
    }
}

@Composable
private fun LiveCoverPreview(
    bitmap: ImageBitmap,
    backgroundColorArgb: Int,
    settings: CoverRenderSettings,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(Color(backgroundColorArgb)),
    ) {
        val layout = CoverImageGenerator.calculateLayout(bitmap.width, bitmap.height, settings)
        val previewScale = maxHeight / CoverImageGenerator.CANVAS_H.toFloat()
        val imageHeight = previewScale * layout.drawH
        val imageWidth = previewScale * layout.drawW
        val cornerRadius = previewScale * settings.cornerRadius
        val shadowOffsetY = previewScale * settings.shadowOffsetY
        val shadowBlur = previewScale * settings.shadowBlur
        val shadowColor = Color.Black.copy(alpha = settings.shadowAlpha.coerceIn(0f, 1f))
        val shape = RoundedCornerShape(cornerRadius)

        if (settings.shadowAlpha > 0f && (settings.shadowBlur > 0f || settings.shadowOffsetY > 0f)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = previewScale * layout.drawX,
                        y = previewScale * layout.drawY + shadowOffsetY,
                    )
                    .size(width = imageWidth, height = imageHeight)
                    .blur(shadowBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(shadowColor, shape)
            )
        }
        Image(
            bitmap = bitmap,
            contentDescription = "封面实时预览",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = previewScale * layout.drawX, y = previewScale * layout.drawY)
                .size(width = imageWidth, height = imageHeight)
                .clip(shape),
            contentScale = ContentScale.FillBounds
        )
    }
}

@Composable
private fun CoverSettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    suffix: String,
    enabled: Boolean,
    onValueChanging: (Float) -> Unit,
    onValueCommitted: (Float) -> Unit,
) {
    var localValue by remember { mutableFloatStateOf(value.coerceIn(valueRange.start, valueRange.endInclusive)) }
    LaunchedEffect(value) {
        localValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(formatSettingValue(localValue, suffix), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = localValue,
            onValueChange = {
                localValue = it
                onValueChanging(it)
            },
            onValueChangeFinished = { onValueCommitted(localValue) },
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatSettingValue(value: Float, suffix: String): String =
    "${value.roundToInt()}$suffix"

// ── 调色板对话框 ──

@Composable
private fun PaletteColorPickerDialog(
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val normalizedInitialHex = remember(initialHex) { ColorHex.normalizeRgbHex(initialHex) }
    val initColor = parseHexColor(normalizedInitialHex)
    var hue by remember { mutableFloatStateOf(hueFromColor(initColor)) }
    var sat by remember { mutableFloatStateOf(satFromColor(initColor)) }
    var value by remember { mutableFloatStateOf(valueFromColor(initColor)) }

    val currentColor = Color.hsv(hue, sat, value)
    val hex by derivedStateOf {
        val r = (currentColor.red * 255 + 0.5f).toInt()
        val g = (currentColor.green * 255 + 0.5f).toInt()
        val b = (currentColor.blue * 255 + 0.5f).toInt()
        "%02X%02X%02X".format(r, g, b)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义背景色", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(260.dp)) {
                Surface(modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(8.dp)), color = currentColor, shape = RoundedCornerShape(8.dp)) {}
                SVPlane(hue = hue, sat = sat, value = value, onSelect = { ns, nv -> sat = ns; value = nv }, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                HueSlider(hue = hue, onHueChange = { hue = it }, modifier = Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("#", style = MaterialTheme.typography.bodyMedium)
                    BasicTextField(
                        value = hex,
                        onValueChange = { input ->
                            val filtered = input.filter { it in "0123456789ABCDEFabcdef" }.take(6)
                            if (filtered.length == 6) {
                                val c = parseHexColor("#$filtered")
                                hue = hueFromColor(c); sat = satFromColor(c); value = valueFromColor(c)
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }
                Text("预设色", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("505A88","3A5070","585858","425A46","584A3E","383838").forEach { h ->
                        val c = parseHexColor("#$h")
                        Surface(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable { hue = hueFromColor(c); sat = satFromColor(c); value = valueFromColor(c) }, color = c, shape = RoundedCornerShape(6.dp)) {}
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(hex) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ── SV 平面 ──

@Composable
private fun SVPlane(
    hue: Float, sat: Float, value: Float,
    onSelect: (sat: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColor = Color.hsv(hue, 1f, 1f)

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ns = (offset.x / size.width).coerceIn(0f, 1f)
                    val nv = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                    onSelect(ns, nv)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val ns = (change.position.x / size.width).coerceIn(0f, 1f)
                    val nv = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onSelect(ns, nv)
                }
            }
    ) {
        // 先铺纯色底色
        drawRect(color = hueColor)
        // 水平白→透明
        drawRect(brush = Brush.linearGradient(listOf(Color.White, Color.Transparent), Offset.Zero, Offset(size.width, 0f)))
        // 垂直透明→黑
        drawRect(brush = Brush.linearGradient(listOf(Color.Transparent, Color.Black), Offset.Zero, Offset(0f, size.height)))

        // 选中指示器
        val cx = sat * size.width
        val cy = (1f - value) * size.height
        drawCircle(Color.White, radius = 6f, center = Offset(cx, cy), style = Stroke(2f))
        drawCircle(Color.Black, radius = 4f, center = Offset(cx, cy), style = Stroke(1.5f))
    }
}

// ── Hue 滑块 ──

@Composable
private fun HueSlider(
    hue: Float, onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColors = remember { (0..36).map { Color.hsv(it * 10f, 1f, 1f) } }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures { offset -> onHueChange((offset.x / size.width).coerceIn(0f, 1f) * 360f) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> onHueChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f) } }
    ) {
        drawRect(brush = Brush.linearGradient(hueColors, Offset.Zero, Offset(size.width, 0f)))
        val cx = hue / 360f * size.width
        drawCircle(Color.White, radius = 8f, center = Offset(cx, size.height / 2), style = Stroke(2.5f))
        drawCircle(Color.Black, radius = 6f, center = Offset(cx, size.height / 2), style = Stroke(1.5f))
    }
}

// ── 工具函数 ──

private fun parseHexColor(hex: String): Color = try {
    Color(ColorHex.parseRgbToArgb(hex, fallbackArgb = 0xFF808080.toInt()))
} catch (_: Exception) { Color.Gray }

private fun hueFromColor(c: Color): Float {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    val delta = max - min
    if (delta == 0f) return 0f
    return when (max) {
        c.red   -> 60f * (((c.green - c.blue) / delta) % 6f)
        c.green -> 60f * (((c.blue - c.red) / delta) + 2f)
        else    -> 60f * (((c.red - c.green) / delta) + 4f)
    }.let { if (it < 0) it + 360f else it }
}

private fun satFromColor(c: Color): Float {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    return if (max == 0f) 0f else (max - min) / max
}

private fun valueFromColor(c: Color): Float = maxOf(c.red, c.green, c.blue)

// ── BgModeSelector ──

@Composable
private fun BgModeSelector(
    bgMode: BackgroundMode,
    onBgModeChange: (BackgroundMode) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("背景:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Box {
            OutlinedButton(
                onClick = { if (enabled) expanded = true }, enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(bgMode.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                BackgroundMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                        onClick = {
                            if (enabled) onBgModeChange(mode)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
