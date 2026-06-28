package io.github.benderblog.cdripshare.util

import org.jetbrains.skia.*
import java.io.File

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

    fun generate(sourceImageFile: File, outputFile: File): File {
        val image = Image.makeFromEncoded(sourceImageFile.readBytes())
        val bgColor = extractDominantColor(image)

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
