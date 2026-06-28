
package io.github.benderblog.cdripshare.util

import kotlin.math.abs

/**
 * 完整 Android Palette 移植 — 6 个颜色配置文件 + 降级链评分。
 *
 * 使用方式：
 * ```
 * val swatches = ColorCutQuantizer.quantize(imageFile, 16)
 * val bgColor = Palette.from(swatches).darkVibrantSwatch?.rgb
 *     ?: Palette.from(swatches).vibrantSwatch?.rgb
 *     ?: ...
 * ```
 */
object Palette {

    data class Swatch(
        val rgb: Int,
        val population: Int,
        val hsl: FloatArray  // [h, s, l]
    )

    // ── 6 个颜色配置文件 ──

    private enum class Profile(
        val minSat: Float, val targetSat: Float,  // saturation range & ideal
        val minLight: Float, val targetLight: Float,  // lightness range & ideal
        val weightSat: Float, val weightLight: Float, val weightPop: Float
    ) {
        VIBRANT(      0.35f, 1f,  0.3f,  0.5f,  0.24f, 0.52f, 0.24f),
        DARK_VIBRANT( 0.35f, 1f,  0f,    0.26f, 0.24f, 0.52f, 0.24f),
        LIGHT_VIBRANT(0.35f, 1f,  0.55f, 0.74f, 0.24f, 0.52f, 0.24f),
        MUTED(        0f,    0.3f, 0.3f,  0.5f,  0.24f, 0.52f, 0.24f),
        DARK_MUTED(   0f,    0.3f, 0f,    0.26f, 0.24f, 0.52f, 0.24f),
        LIGHT_MUTED(  0f,    0.3f, 0.55f, 0.74f, 0.24f, 0.52f, 0.24f);

        fun isWithinRange(sat: Float, light: Float): Boolean =
            sat in minSat..1f && light in (if (minLight == 0f) 0f else minLight)..1f
    }

    // ── 对外 API ──

    fun from(quantizedSwatches: List<ColorCutQuantizer.Swatch>): PaletteImage {
        val maxPop = quantizedSwatches.maxOf { it.population }
        val swatches = quantizedSwatches.map { qs ->
            Swatch(
                rgb = qs.rgb,
                population = qs.population,
                hsl = rgbToHsl(qs.rgb)
            )
        }
        return PaletteImage(swatches, maxPop)
    }

    class PaletteImage(
        private val swatches: List<Swatch>,
        private val maxPop: Int
    ) {
        val vibrantSwatch: Swatch? by lazy { generateSwatch(Profile.VIBRANT) }
        val darkVibrantSwatch: Swatch? by lazy { generateSwatch(Profile.DARK_VIBRANT) }
        val lightVibrantSwatch: Swatch? by lazy { generateSwatch(Profile.LIGHT_VIBRANT) }
        val mutedSwatch: Swatch? by lazy { generateSwatch(Profile.MUTED) }
        val darkMutedSwatch: Swatch? by lazy { generateSwatch(Profile.DARK_MUTED) }
        val lightMutedSwatch: Swatch? by lazy { generateSwatch(Profile.LIGHT_MUTED) }

        /** 降级链获取背景色：DarkVibrant → Vibrant → DarkMuted → Muted → Dominant */
        fun bestBackgroundSwatch(): Swatch {
            return darkVibrantSwatch
                ?: vibrantSwatch
                ?: darkMutedSwatch
                ?: mutedSwatch
                ?: swatches.maxByOrNull { it.population }!!
        }

        private fun generateSwatch(profile: Profile): Swatch? {
            val candidates = swatches.filter { profile.isWithinRange(it.hsl[1], it.hsl[2]) }
            if (candidates.isEmpty()) return null
            val maxPopF = maxPop.toFloat()
            var best: Swatch = candidates.first()
            var bestScore = -1f
            for (s in candidates) {
                val score = profile.weightSat * (1f - abs(s.hsl[1] - profile.targetSat)) +
                            profile.weightLight * (1f - abs(s.hsl[2] - profile.targetLight)) +
                            profile.weightPop * (s.population / maxPopF)
                if (score > bestScore) { bestScore = score; best = s }
            }
            return best
        }
    }

    // ── RGB → HSL ──

    private fun rgbToHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        val delta = maxC - minC
        val l = (maxC + minC) / 2f
        val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))
        val h = when {
            delta == 0f -> 0f
            maxC == r   -> 60f * (((g - b) / delta) % 6f)
            maxC == g   -> 60f * (((b - r) / delta) + 2f)
            else        -> 60f * (((r - g) / delta) + 4f)
        }
        return floatArrayOf(if (h < 0) h + 360f else h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }
}
