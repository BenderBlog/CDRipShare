package io.github.benderblog.cdripshare.util

import org.jetbrains.skia.*
import java.io.File
import io.github.benderblog.cdripshare.model.BackgroundMode
import kotlin.math.max
import kotlin.math.min

/**
 * 将一张图片生成 1920×1080 封面图：
 * - 背景色：从原图提取主色（缩放到 1×1 取平均）
 * - 图片：缩放到高度 800，居中
 * - 圆角 + 阴影
 * - 输出无 alpha 通道（兼容 x265）
 */
object CoverImageGenerator {

    private const val CANVAS_W = 1920
    private const val CANVAS_H = 1080
    private const val IMAGE_TARGET_H = 800f
    private const val CORNER_RADIUS = 24f
    private const val SHADOW_DX = 0f
    private const val SHADOW_DY = 8f
    private const val SHADOW_SIGMA = 24f

    private val samplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

    /**
     * ARGB → HSL 分量数组 [hue=0..360, saturation=0..1, lightness=0..1]
     */
    private fun rgbToHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f

        val maxC = max(max(r, g), b)
        val minC = min(min(r, g), b)
        val delta = maxC - minC

        val l = (maxC + minC) / 2f
        val s = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * l - 1f))
        val h = when {
            delta == 0f -> 0f
            maxC == r   -> 60f * (((g - b) / delta) % 6f)
            maxC == g   -> 60f * (((b - r) / delta) + 2f)
            else        -> 60f * (((r - g) / delta) + 4f)
        }

        return floatArrayOf(if (h < 0) h + 360f else h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    /**
     * HSL → ARGB (alpha 固定 0xFF)
     */
    private fun hslToRgb(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val (r1, g1, b1) = when {
            h < 60   -> Triple(c, x, 0f)
            h < 120  -> Triple(x, c, 0f)
            h < 180  -> Triple(0f, c, x)
            h < 240  -> Triple(0f, x, c)
            h < 300  -> Triple(x, 0f, c)
            else     -> Triple(c, 0f, x)
        }

        val r = ((r1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)

        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * 对提取的主色做后处理：保留色相，饱和度降到 30%，亮度压到 15%~25% 之间
     */
    private fun adjustBackgroundColor(argb: Int): Int {
        val hsl = rgbToHsl(argb)
        val sat = (hsl[1] * 0.3f).coerceIn(0f, 1f)
        val light = (hsl[2] * 0.5f).coerceIn(0.10f, 0.25f)
        return hslToRgb(hsl[0], sat, light)
    }

    fun generate(sourceImageFile: File, outputFile: File, bgMode: BackgroundMode = BackgroundMode.Auto): File {
        val image = Image.makeFromEncoded(sourceImageFile.readBytes())
        val bgColor = when (bgMode) {
            BackgroundMode.Auto -> adjustBackgroundColor(extractDominantColor(image))
            else -> bgMode.colorHex or (0xFF shl 24)
        }

        val scale = IMAGE_TARGET_H / image.height
        val drawW = image.width * scale
        val drawH = IMAGE_TARGET_H
        val drawX = (CANVAS_W - drawW) / 2f
        val drawY = (CANVAS_H - drawH) / 2f

        // 阴影需要半透明，先画在 Premul 画布上
        val premulSurface = Surface.makeRaster(ImageInfo.makeN32Premul(CANVAS_W, CANVAS_H))
        val canvas = premulSurface.canvas
        canvas.clear(bgColor)
        drawImageWithShadow(canvas, image, drawX, drawY, drawW, drawH)

        // 合成到无 alpha 的画布，去掉透明通道（x265 不支持 alpha）
        val opaqueInfo = ImageInfo(CANVAS_W, CANVAS_H, ColorType.N32, ColorAlphaType.OPAQUE)
        val opaqueSurface = Surface.makeRaster(opaqueInfo)
        opaqueSurface.canvas.drawImage(premulSurface.makeImageSnapshot(), 0f, 0f)

        val pngData = opaqueSurface.makeImageSnapshot()
            .encodeToData(EncodedImageFormat.PNG)
            ?: throw RuntimeException("PNG 编码失败")
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(pngData.bytes)
        return outputFile
    }

    private fun extractDominantColor(image: Image): Int {
        val info = ImageInfo.makeN32Premul(1, 1)
        val tiny = Surface.makeRaster(info)
        val src = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
        val dst = Rect(0f, 0f, 1f, 1f)
        tiny.canvas.drawImageRect(image, src, dst, null)
        val bmp = Bitmap()
        bmp.allocPixels(info)
        tiny.makeImageSnapshot().readPixels(bmp, 0, 0)
        return bmp.getColor(0, 0)
    }

    private fun drawImageWithShadow(
        canvas: Canvas, image: Image,
        x: Float, y: Float, w: Float, h: Float,
    ) {
        val outset = SHADOW_SIGMA * 3
        val layerBounds = Rect(
            x - outset, y - outset,
            x + w + outset, y + h + outset
        )
        val shadowFilter = ImageFilter.makeDropShadow(
            SHADOW_DX, SHADOW_DY,
            SHADOW_SIGMA, SHADOW_SIGMA,
            0xAA000000.toInt(), null, null
        )
        val layerPaint = Paint().apply { imageFilter = shadowFilter }

        canvas.saveLayer(layerBounds, layerPaint)
        try {
            val rrect = RRect.makeLTRB(x, y, x + w, y + h, CORNER_RADIUS)
            canvas.clipRRect(rrect, ClipMode.INTERSECT, true)
            val src = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
            val dst = Rect(x, y, x + w, y + h)
            canvas.drawImageRect(image, src, dst, samplingMode, null, false)
        } finally {
            canvas.restore()
        }
    }
}
