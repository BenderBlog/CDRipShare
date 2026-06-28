package io.github.benderblog.cdripshare.util

import kotlin.math.max
import kotlin.math.min
import org.jetbrains.skia.*

/**
 * 颜色量化器 — Android Palette 风格的 MMCQ 引擎。
 *
 * 流程：
 * 1. 缩小图片 ~112×112
 * 2. 5-bit 量化 → 32768 色
 * 3. 按颜色体积递归二分切割，得到最多 [maxColors] 个 Swatch
 */
object ColorCutQuantizer {

    private const val QUANTIZE_WORD_WIDTH = 5
    private const val QUANTIZE_WORD_MASK = (1 shl QUANTIZE_WORD_WIDTH) - 1
    private const val VBOX_MAX_DIM = 1 shl QUANTIZE_WORD_WIDTH  // 32

    data class Swatch(
        val red: Int, val green: Int, val blue: Int,
        val population: Int
    ) {
        val rgb: Int get() = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    fun quantize(sourceImageFile: java.io.File, maxColors: Int = 16): List<Swatch> {
        val image = Image.makeFromEncoded(sourceImageFile.readBytes())
        val scaled = scaleDown(image, 112)
        val pixels = readPixels(scaled)
        val colorPopulations = quantizeFromPixels(pixels)
        return if (colorPopulations.size <= maxColors) {
            colorPopulations.keys.map { c ->
                Swatch(quantizedRed(c), quantizedGreen(c), quantizedBlue(c), colorPopulations[c]!!)
            }
        } else {
            medianCut(colorPopulations, maxColors)
        }
    }

    // ── 缩小图片 ──

    private fun scaleDown(image: Image, maxDim: Int): Image {
        val scale = min(maxDim.toFloat() / image.width, maxDim.toFloat() / image.height).coerceAtMost(1f)
        if (scale >= 1f) return image
        val w = (image.width * scale).toInt().coerceAtLeast(1)
        val h = (image.height * scale).toInt().coerceAtLeast(1)
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(w, h))
        surface.canvas.drawImageRect(
            image,
            org.jetbrains.skia.Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()),
            org.jetbrains.skia.Rect(0f, 0f, w.toFloat(), h.toFloat()),
            null
        )
        return surface.makeImageSnapshot()
    }

    // ── 像素读取 & 量化 ──

    private fun readPixels(image: Image): IntArray {
        val w = image.width
        val h = image.height
        val info = ImageInfo.makeN32Premul(w, h)
        val bmp = Bitmap()
        bmp.allocPixels(info)
        image.readPixels(bmp, 0, 0)
        val array = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val px = bmp.getColor(x, y)
                array[y * w + x] = px
            }
        }
        return array
    }

    private fun quantizeFromPixels(pixels: IntArray): MutableMap<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        for (px in pixels) {
            val a = (px shr 24) and 0xFF
            if (a < 125) continue  // 跳过透明像素
            val r = ((px shr 16) and 0xFF) shr (8 - QUANTIZE_WORD_WIDTH)
            val g = ((px shr 8) and 0xFF) shr (8 - QUANTIZE_WORD_WIDTH)
            val b = (px and 0xFF) shr (8 - QUANTIZE_WORD_WIDTH)
            val color = (r shl (QUANTIZE_WORD_WIDTH * 2)) or
                        (g shl QUANTIZE_WORD_WIDTH) or b
            map[color] = (map[color] ?: 0) + 1
        }
        return map
    }

    // ── MMCQ 体积切割 ──

    private fun medianCut(colorPopulations: MutableMap<Int, Int>, maxColors: Int): List<Swatch> {
        val vboxes = mutableListOf<VBox>()
        vboxes.add(VBox(colorPopulations))
        splitBoxes(vboxes, maxColors)
        return generateSwatches(vboxes)
    }

    private fun splitBoxes(vboxes: MutableList<VBox>, maxColors: Int) {
        while (vboxes.size < maxColors) {
            var bestVBox: VBox? = null
            var bestIndex = -1
            for ((i, vbox) in vboxes.withIndex()) {
                if (vbox.canSplit && (bestVBox == null || vbox.volume > bestVBox.volume)) {
                    bestVBox = vbox
                    bestIndex = i
                }
            }
            if (bestVBox == null) break
            val split = bestVBox.split()
            if (split == null) break
            vboxes[bestIndex] = split.first
            vboxes.add(bestIndex + 1, split.second)
        }
    }

    private fun generateSwatches(vboxes: List<VBox>): List<Swatch> {
        return vboxes.map { vbox ->
            val colors = vbox.colors
            var sumR = 0L; var sumG = 0L; var sumB = 0L
            var totalPop = 0
            for ((color, pop) in colors) {
                sumR += quantizedRed(color) * pop
                sumG += quantizedGreen(color) * pop
                sumB += quantizedBlue(color) * pop
                totalPop += pop
            }
            Swatch(
                red = (sumR / totalPop).toInt(),
                green = (sumG / totalPop).toInt(),
                blue = (sumB / totalPop).toInt(),
                population = totalPop
            )
        }.sortedByDescending { it.population }
    }

    // ── VBox ──

    private class VBox(val colors: Map<Int, Int>) {
        private var minR = VBOX_MAX_DIM
        private var maxR = 0
        private var minG = VBOX_MAX_DIM
        private var maxG = 0
        private var minB = VBOX_MAX_DIM
        private var maxB = 0

        val volume: Int
            get() = (maxR - minR + 1) * (maxG - minG + 1) * (maxB - minB + 1)

        val canSplit: Boolean
            get() = colors.size > 1

        private val longestDimension: Int
            get() {
                val rLen = maxR - minR
                val gLen = maxG - minG
                val bLen = maxB - minB
                return if (rLen >= gLen && rLen >= bLen) 0
                else if (gLen >= rLen && gLen >= bLen) 1
                else 2
            }

        init {
            for (color in colors.keys) {
                val r = quantizedRed(color); val g = quantizedGreen(color); val b = quantizedBlue(color)
                if (r < minR) minR = r; if (r > maxR) maxR = r
                if (g < minG) minG = g; if (g > maxG) maxG = g
                if (b < minB) minB = b; if (b > maxB) maxB = b
            }
        }

        fun split(): Pair<VBox, VBox>? {
            if (!canSplit) return null
            val dim = longestDimension
            val sorted = colors.entries.sortedBy { (color, _) ->
                when (dim) {
                    0 -> quantizedRed(color)
                    1 -> quantizedGreen(color)
                    else -> quantizedBlue(color)
                }
            }
            val totalPop = sorted.sumOf { it.value }
            var runningPop = 0
            var splitIdx = 0
            for ((i, entry) in sorted.withIndex()) {
                runningPop += entry.value
                splitIdx = i
                if (runningPop >= totalPop / 2) break
            }
            if (splitIdx == 0) return null
            val lower = sorted.subList(0, splitIdx).associate { it.key to it.value }
            val upper = sorted.subList(splitIdx, sorted.size).associate { it.key to it.value }
            if (lower.isEmpty() || upper.isEmpty()) return null
            return VBox(lower) to VBox(upper)
        }
    }

    // ── 工具 ──

    private fun quantizedRed(color: Int) = (color shr (QUANTIZE_WORD_WIDTH * 2)) and QUANTIZE_WORD_MASK
    private fun quantizedGreen(color: Int) = (color shr QUANTIZE_WORD_WIDTH) and QUANTIZE_WORD_MASK
    private fun quantizedBlue(color: Int) = color and QUANTIZE_WORD_MASK
}
