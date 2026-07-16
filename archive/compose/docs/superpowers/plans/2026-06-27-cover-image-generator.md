# Cover Image Generator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 Skia 生成 1920×1080 封面图：从导入图片提取主色作为背景，将图片缩放至高度 800 居中，添加阴影，输出 PNG。

**Architecture:** 新增 `CoverImageGenerator.kt` 工具类，使用 Compose Desktop 内置的 `org.jetbrains.skia` API 在内存中渲染封面图。流程：加载图片 → 缩放到 1×1 提取主色 → 创建 1920×1080 画布填充背景色 → 居中绘制缩放后图片（带圆角阴影） → 保存为临时 PNG。在 `MainViewModel.startSynthesis()` 中，FFmpeg 合成前调用此工具生成封面，替代原始图片传入 `VideoMuxer`。

**Tech Stack:** Kotlin, org.jetbrains.skia (随 Compose Desktop 自带，无需额外依赖), kotlinx.coroutines

**无需修改 `build.gradle.kts`** — Skia API 已通过 `compose.desktop.currentOs` 传递。

---

## File Structure

| 操作 | 文件路径 | 职责 |
|------|---------|------|
| Create | `src/main/kotlin/com/benderblog/videoaudio/util/CoverImageGenerator.kt` | 封面图生成核心逻辑 |
| Modify | `src/main/kotlin/com/benderblog/videoaudio/viewmodel/MainViewModel.kt` | 在合成流程中调用封面生成 |
| Modify | `src/main/kotlin/com/benderblog/videoaudio/ffmpeg/VideoMuxer.kt` | 接受封面图路径（可选，如果生成的封面替代原图） |

---

## Task 1: 创建 CoverImageGenerator 工具类

**Files:**
- Create: `src/main/kotlin/com/benderblog/videoaudio/util/CoverImageGenerator.kt`

- [ ] **Step 1: 创建文件，实现主色提取函数 `extractDominantColor`**

思路：将图片缩放到 1×1 像素，取该像素颜色作为平均色（Skia 的缩放算法会自动做平均）。

```kotlin
package com.benderblog.videoaudio.util

import org.jetbrains.skia.*
import java.io.File

object CoverImageGenerator {

    private const val CANVAS_W = 1920
    private const val CANVAS_H = 1080
    private const val IMAGE_TARGET_H = 800f
    private const val CORNER_RADIUS = 24f
    private const val SHADOW_DX = 0f
    private const val SHADOW_DY = 8f
    private const val SHADOW_SIGMA = 24f

    /**
     * 将图片缩放到 1×1，取像素颜色作为主色。
     * Skia 的缩放滤镜会对所有像素做加权平均。
     */
    private fun extractDominantColor(image: Image): Int {
        val tinySurface = Surface.makeRaster(
            ImageInfo.makeN32Premul(1, 1)
        )
        val canvas = tinySurface.canvas
        canvas.drawImageRect(
            image,
            Rect(0f, 0f, 1f, 1f),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
        )
        val pixels = tinySurface.makeImageSnapshot()
            .readPixels(0, 0) ?: return 0xFF222222.toInt()
        // Bitmap readPixels 返回 BGRA 或 RGBA 取决于平台，
        // 但 makeImageSnapshot() 返回的是 N32 premultiplied，
        // 颜色分量顺序在 JVM desktop 上是 ARGB (packed int)
        return pixels.getColor(0, 0)
    }
}
```

注意：`Surface.readPixels` 返回的是 `Bitmap`，但上面的写法有误。正确做法是用 `Bitmap` 手动读取。在 Step 2 中修正。

- [ ] **Step 2: 实现完整的 `generate` 方法**

替换 Step 1 的文件内容为完整实现：

```kotlin
package com.benderblog.videoaudio.util

import org.jetbrains.skia.*
import java.io.File

object CoverImageGenerator {

    private const val CANVAS_W = 1920
    private const val CANVAS_H = 1080
    private const val IMAGE_TARGET_H = 800f
    private const val CORNER_RADIUS = 24f
    private const val SHADOW_DX = 0f
    private const val SHADOW_DY = 8f
    private const val SHADOW_SIGMA = 24f

    /**
     * 生成 1920×1080 封面图并保存为 PNG。
     * @param sourceImageFile 原始图片文件
     * @param outputFile 输出 PNG 文件路径
     * @return outputFile
     */
    fun generate(sourceImageFile: File, outputFile: File): File {
        val imageBytes = sourceImageFile.readBytes()
        val image = Image.makeFromEncoded(imageBytes)

        // 1. 提取主色
        val bgColor = extractDominantColor(image)

        // 2. 计算缩放：目标高度 800，按比例缩放宽度
        val scale = IMAGE_TARGET_H / image.height
        val drawW = image.width * scale
        val drawH = IMAGE_TARGET_H
        val drawX = (CANVAS_W - drawW) / 2f
        val drawY = (CANVAS_H - drawH) / 2f

        // 3. 创建画布
        val surface = Surface.makeRaster(
            ImageInfo.makeN32Premul(CANVAS_W, CANVAS_H)
        )
        val canvas = surface.canvas

        // 4. 填充背景色
        canvas.clear(bgColor)

        // 5. 绘制带阴影的圆角图片
        drawImageWithShadow(canvas, image, drawX, drawY, drawW, drawH)

        // 6. 保存为 PNG
        val snapshot = surface.makeImageSnapshot()
        val pngData = snapshot.encodeToData(EncodedImageFormat.PNG)
            ?: throw RuntimeException("PNG 编码失败")
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(pngData.bytes)

        return outputFile
    }

    /**
     * 将图片缩放到 1×1，取像素颜色作为主色。
     */
    private fun extractDominantColor(image: Image): Int {
        val tinyInfo = ImageInfo.makeN32Premul(1, 1)
        val tinySurface = Surface.makeRaster(tinyInfo)
        val canvas = tinySurface.canvas
        canvas.drawImageRect(
            image,
            Rect(0f, 0f, 1f, 1f),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
        )
        val snapshot = tinySurface.makeImageSnapshot()
        val bitmap = Bitmap()
        bitmap.allocPixels(tinyInfo)
        snapshot.readPixels(bitmap, 0, 0)
        return bitmap.getColor(0, 0)
    }

    /**
     * 在画布上绘制带圆角阴影的图片。
     *
     * 技巧：先 saveLayer（bounds 包含阴影区域），
     * 在 layer 内用 clipRRect 裁剪圆角并绘制图片，
     * restore 时 layer 的 Paint（含 ImageFilter.makeDropShadow）生效，
     * 阴影根据 layer 内容的 alpha 形状（圆角矩形）生成。
     */
    private fun drawImageWithShadow(
        canvas: Canvas,
        image: Image,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
    ) {
        // saveLayer 的 bounds 需要包含阴影扩散范围
        val shadowOutset = SHADOW_SIGMA * 3
        val layerBounds = Rect(
            x - shadowOutset,
            y - shadowOutset,
            x + w + shadowOutset,
            y + h + shadowOutset
        )

        // Layer Paint：restore 时应用阴影滤镜
        val layerPaint = Paint().apply {
            setImageFilter(
                ImageFilter.makeDropShadow(
                    dx = SHADOW_DX,
                    dy = SHADOW_DY,
                    sigmaX = SHADOW_SIGMA,
                    sigmaY = SHADOW_SIGMA,
                    color = 0xAA000000.toInt(),  // 半透明黑色
                )
            )
        }

        canvas.saveLayer(layerBounds, layerPaint)
        try {
            // 圆角裁剪
            val rrect = RRect.makeLTRB(
                x, y, x + w, y + h, CORNER_RADIUS
            )
            canvas.clipRRect(rrect, ClipMode.INTERSECT, true)

            // 绘制图片
            canvas.drawImageRect(
                image,
                Rect(x, y, x + w, y + h),
                FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
            )
        } finally {
            canvas.restore()
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd "/Users/superbart/Documents/图片和音频转成视频" && ./gradlew :compileKotlin
```

Expected: BUILD SUCCESSFUL（只是新增文件，不影响已有代码）

---

## Task 2: 集成到合成流程

**Files:**
- Modify: `src/main/kotlin/com/benderblog/videoaudio/viewmodel/MainViewModel.kt`

- [ ] **Step 1: 在 `startSynthesis` 中，FFmpeg 合成前调用封面生成**

找到 `startSynthesis` 方法中调用 `videoMuxer.mux(...)` 之前的位置，插入封面生成逻辑：

```kotlin
// 在 val imageFile = viewModel.imageFile.value 之后，调用 mux 之前：
val coverFile = File(tempDir, "cover.png")
CoverImageGenerator.generate(viewModel.imageFile.value!!, coverFile)
onLog("封面图已生成: ${coverFile.absolutePath}")

// 然后将 coverFile 传给 videoMuxer.mux() 替代原始 imageFile
```

具体修改位置：在 `launch(Dispatchers.IO)` 块内，`videoMuxer.mux(...)` 调用之前。

- [ ] **Step 2: 确认 VideoMuxer 无需修改**

`VideoMuxer.mux()` 接受 `imageFile: File`，生成的封面图是标准 PNG，FFmpeg 会正常处理。无需改动 `VideoMuxer`。

- [ ] **Step 3: 编译并运行验证**

```bash
cd "/Users/superbart/Documents/图片和音频转成视频" && ./gradlew :run
```

Expected: 应用启动后，选择图片和音频，点击合成，在日志中看到 "封面图已生成" 输出。

---

## Task 3: 验证输出质量

- [ ] **Step 1: 手动测试**

1. 选择一张专辑封面图片（正方形或非正方形均可）
2. 添加若干音频文件
3. 点击合成
4. 检查日志输出确认封面图已生成
5. 用系统预览打开生成的 `cover.png`，确认：
   - 画布 1920×1080
   - 图片居中，高度约 800px
   - 有圆角效果
   - 有阴影效果
   - 背景色与图片主色调匹配

- [ ] **Step 2: 检查边界情况**

测试非正方形图片（如 16:9 风景照、竖版海报），确认缩放和居中行为正确。

