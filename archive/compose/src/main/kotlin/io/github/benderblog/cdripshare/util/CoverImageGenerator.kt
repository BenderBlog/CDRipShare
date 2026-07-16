package io.github.benderblog.cdripshare.util

import io.github.benderblog.cdripshare.model.BackgroundMode
import io.github.benderblog.cdripshare.model.CoverRenderSettings
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class CoverImageLayout(
    val drawX: Float,
    val drawY: Float,
    val drawW: Float,
    val drawH: Float,
    val layerBounds: Rect,
)

object CoverImageGenerator {

    const val CANVAS_W = 1920
    const val CANVAS_H = 1080
    private const val SHADOW_DX = 0f
    private const val SHADOW_OUTSET_MULTIPLIER = 4f
    private val samplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

    fun generate(
        sourceImageFile: File,
        outputFile: File,
        bgMode: BackgroundMode = BackgroundMode.Auto,
        customColor: Int = 0xFF585858L.toInt(),
        settings: CoverRenderSettings = CoverRenderSettings(),
    ): File {
        val image = Image.makeFromEncoded(sourceImageFile.readBytes())
        val renderSettings = settings.normalized()
        val bgColor = resolveBackgroundColor(image, bgMode, customColor)
        val layout = calculateLayout(image.width, image.height, renderSettings)
        val premulSurface = Surface.makeRaster(ImageInfo.makeN32Premul(CANVAS_W, CANVAS_H))
        val canvas = premulSurface.canvas
        canvas.clear(bgColor)
        drawImageWithShadow(canvas, image, layout, renderSettings)
        val opaqueInfo = ImageInfo(CANVAS_W, CANVAS_H, ColorType.N32, ColorAlphaType.OPAQUE)
        val opaqueSurface = Surface.makeRaster(opaqueInfo)
        opaqueSurface.canvas.drawImage(premulSurface.makeImageSnapshot(), 0f, 0f)
        val pngData = opaqueSurface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)
            ?: throw RuntimeException("PNG 编码失败")
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(pngData.bytes)
        return outputFile
    }

    fun resolveBackgroundColor(
        sourceImageFile: File,
        bgMode: BackgroundMode = BackgroundMode.Auto,
        customColor: Int = 0xFF585858L.toInt(),
    ): Int {
        return CoverColorExtractor.resolveBackgroundColor(sourceImageFile, bgMode, customColor)
    }

    fun resolveSolidBackgroundColor(bgMode: BackgroundMode, customColor: Int = 0xFF585858L.toInt()): Int? =
        CoverColorExtractor.resolveSolidBackgroundColor(bgMode, customColor)

    private fun resolveBackgroundColor(image: Image, bgMode: BackgroundMode, customColor: Int): Int =
        CoverColorExtractor.resolveBackgroundColor(image, bgMode, customColor)

    fun calculateLayout(
        imageWidth: Int,
        imageHeight: Int,
        settings: CoverRenderSettings,
    ): CoverImageLayout {
        val renderSettings = settings.normalized()
        val aspectRatio = imageWidth.toFloat() / imageHeight.coerceAtLeast(1).toFloat()
        val shadowExtents = calculateShadowExtents(renderSettings)
        val maxImageHeightByCanvas = (CANVAS_H - shadowExtents.top - shadowExtents.bottom)
            .coerceAtLeast(1f)
        val maxImageHeightByWidth = if (aspectRatio > 0f) {
            ((CANVAS_W - shadowExtents.left - shadowExtents.right) / aspectRatio).coerceAtLeast(1f)
        } else {
            maxImageHeightByCanvas
        }
        val drawH = min(renderSettings.imageHeight, min(maxImageHeightByCanvas, maxImageHeightByWidth))
        val drawW = drawH * aspectRatio
        val groupW = drawW + shadowExtents.left + shadowExtents.right
        val groupH = drawH + shadowExtents.top + shadowExtents.bottom
        val drawX = shadowExtents.left + (CANVAS_W - groupW) / 2f
        val drawY = shadowExtents.top + (CANVAS_H - groupH) / 2f
        return CoverImageLayout(
            drawX = drawX,
            drawY = drawY,
            drawW = drawW,
            drawH = drawH,
            layerBounds = Rect(
                drawX - shadowExtents.left,
                drawY - shadowExtents.top,
                drawX + drawW + shadowExtents.right,
                drawY + drawH + shadowExtents.bottom,
            ),
        )
    }

    private fun calculateShadowExtents(settings: CoverRenderSettings): ShadowExtents {
        if (settings.shadowAlpha <= 0f || (settings.shadowBlur <= 0f && settings.shadowOffsetY <= 0f)) {
            return ShadowExtents()
        }
        val blurOutset = settings.shadowBlur * SHADOW_OUTSET_MULTIPLIER
        return ShadowExtents(
            left = max(0f, blurOutset - SHADOW_DX),
            top = max(0f, blurOutset - settings.shadowOffsetY),
            right = max(0f, blurOutset + SHADOW_DX),
            bottom = max(0f, blurOutset + settings.shadowOffsetY),
        )
    }

    // ── 绘图 ──

    private fun drawImageWithShadow(
        canvas: Canvas,
        image: Image,
        layout: CoverImageLayout,
        settings: CoverRenderSettings,
    ) {
        if (settings.shadowAlpha <= 0f || (settings.shadowBlur <= 0f && settings.shadowOffsetY <= 0f)) {
            drawRoundedImage(
                canvas = canvas,
                image = image,
                x = layout.drawX,
                y = layout.drawY,
                w = layout.drawW,
                h = layout.drawH,
                cornerRadius = settings.cornerRadius,
            )
            return
        }

        val alpha = (settings.shadowAlpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        val shadowFilter = ImageFilter.makeDropShadow(
            SHADOW_DX,
            settings.shadowOffsetY,
            settings.shadowBlur,
            settings.shadowBlur,
            alpha shl 24,
            null,
            null,
        )
        val layerPaint = Paint().apply { imageFilter = shadowFilter }
        canvas.saveLayer(layout.layerBounds, layerPaint)
        try {
            drawRoundedImage(
                canvas = canvas,
                image = image,
                x = layout.drawX,
                y = layout.drawY,
                w = layout.drawW,
                h = layout.drawH,
                cornerRadius = settings.cornerRadius,
            )
        } finally { canvas.restore() }
    }

    private fun drawRoundedImage(
        canvas: Canvas,
        image: Image,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        cornerRadius: Float,
    ) {
        canvas.save()
        try {
            val rrect = RRect.makeLTRB(x, y, x + w, y + h, cornerRadius)
            canvas.clipRRect(rrect, ClipMode.INTERSECT, true)
            val src = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
            val dst = Rect(x, y, x + w, y + h)
            canvas.drawImageRect(image, src, dst, samplingMode, null, false)
        } finally { canvas.restore() }
    }

    private data class ShadowExtents(
        val left: Float = 0f,
        val top: Float = 0f,
        val right: Float = 0f,
        val bottom: Float = 0f,
    )
}
