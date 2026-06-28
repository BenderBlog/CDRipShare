package io.github.benderblog.cdripshare.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun ImagePickerPanel(
    imageFile: File?,
    coverPreview: ImageBitmap?,
    onSelect: () -> Unit,
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
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
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
                    .width(200.dp)
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
    }
}
