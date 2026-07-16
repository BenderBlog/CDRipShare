package io.github.benderblog.cdripshare.util

import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import io.github.benderblog.cdripshare.model.BackgroundMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.abs

data class CoverMaterialPalette(
    val seed: Int,
    val coverBackground: Int,
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val secondary: Int,
    val tertiary: Int,
    val onBackground: Int,
    val onSurface: Int,
) {
    val roleColors: List<Int>
        get() = listOf(
            background,
            surface,
            surfaceVariant,
            primary,
            onPrimary,
            primaryContainer,
            secondary,
            tertiary,
            onBackground,
            onSurface,
        )

    companion object {
        fun fromSeed(seed: Int, dark: Boolean = true): CoverMaterialPalette {
            val scheme = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
            val colors = MaterialDynamicColors()
            return CoverMaterialPalette(
                seed = opaque(seed),
                coverBackground = opaque(scheme.primaryPalette.tone(COVER_BACKGROUND_TONE)),
                background = opaque(colors.background().getArgb(scheme)),
                surface = opaque(colors.surface().getArgb(scheme)),
                surfaceVariant = opaque(colors.surfaceVariant().getArgb(scheme)),
                primary = opaque(colors.primary().getArgb(scheme)),
                onPrimary = opaque(colors.onPrimary().getArgb(scheme)),
                primaryContainer = opaque(colors.primaryContainer().getArgb(scheme)),
                secondary = opaque(colors.secondary().getArgb(scheme)),
                tertiary = opaque(colors.tertiary().getArgb(scheme)),
                onBackground = opaque(colors.onBackground().getArgb(scheme)),
                onSurface = opaque(colors.onSurface().getArgb(scheme)),
            )
        }

        private fun opaque(color: Int): Int = color or (0xFF shl 24)

        private const val COVER_BACKGROUND_TONE = 42
    }
}

data class ExtractedCoverColors(
    val swatches: List<ColorCutQuantizer.Swatch>,
    val dominant: Int,
    val autoSeed: Int,
    val paletteSeed: Int,
    val autoMaterialPalette: CoverMaterialPalette,
    val paletteMaterialPalette: CoverMaterialPalette,
) {
    val autoBackground: Int
        get() = autoMaterialPalette.coverBackground

    val paletteBackground: Int
        get() = paletteMaterialPalette.coverBackground
}

object CoverColorExtractor {
    fun extract(sourceImageFile: File): ExtractedCoverColors {
        val image = Image.makeFromEncoded(sourceImageFile.readBytes())
        return extract(image)
    }

    fun extract(image: Image): ExtractedCoverColors {
        val swatches = ColorCutQuantizer.quantize(image, 16)
        val dominant = extractDominantColor(image)
        val autoSeed = if (swatches.isEmpty()) dominant else scoreSwatches(swatches)
        val paletteSeed = if (swatches.isEmpty()) dominant else selectBackgroundSeed(swatches)
        return ExtractedCoverColors(
            swatches = swatches,
            dominant = dominant,
            autoSeed = autoSeed,
            paletteSeed = paletteSeed,
            autoMaterialPalette = CoverMaterialPalette.fromSeed(autoSeed),
            paletteMaterialPalette = CoverMaterialPalette.fromSeed(paletteSeed),
        )
    }

    fun resolveBackgroundColor(
        sourceImageFile: File,
        bgMode: BackgroundMode = BackgroundMode.Auto,
        customColor: Int = 0xFF585858L.toInt(),
    ): Int {
        val image = Image.makeFromEncoded(sourceImageFile.readBytes())
        return resolveBackgroundColor(image, bgMode, customColor)
    }

    fun resolveBackgroundColor(
        image: Image,
        bgMode: BackgroundMode = BackgroundMode.Auto,
        customColor: Int = 0xFF585858L.toInt(),
    ): Int {
        resolveSolidBackgroundColor(bgMode, customColor)?.let { return it }
        val colors = extract(image)
        return when (bgMode) {
            BackgroundMode.Auto -> colors.autoBackground
            BackgroundMode.AutoPalette -> colors.paletteBackground
            else -> error("Solid background mode should have resolved before extraction.")
        }
    }

    fun resolveSolidBackgroundColor(
        bgMode: BackgroundMode,
        customColor: Int = 0xFF585858L.toInt(),
    ): Int? =
        when (bgMode) {
            BackgroundMode.Auto, BackgroundMode.AutoPalette -> null
            BackgroundMode.Custom -> customColor or (0xFF shl 24)
            else -> bgMode.colorHex or (0xFF shl 24)
        }

    private fun scoreSwatches(swatches: List<ColorCutQuantizer.Swatch>): Int {
        val maxPop = swatches.maxOf { it.population }.toFloat()
        var bestRgb = swatches.first().rgb
        var bestScore = -1.0
        for (s in swatches) {
            val hct = Hct.fromInt(s.rgb)
            val chroma = (hct.chroma / 96.0).coerceIn(0.0, 1.0)
            val toneFit = (1.0 - abs(hct.tone - 55.0) / 55.0).coerceIn(0.0, 1.0)
            val population = (s.population / maxPop).toDouble()
            val score = chroma * 0.45 + toneFit * 0.25 + population * 0.30
            if (score > bestScore) {
                bestScore = score
                bestRgb = s.rgb
            }
        }
        return bestRgb
    }

    private fun selectBackgroundSeed(swatches: List<ColorCutQuantizer.Swatch>): Int {
        val maxPop = swatches.maxOf { it.population }.toFloat()
        var bestRgb = swatches.first().rgb
        var bestScore = -1.0
        for (s in swatches) {
            val hct = Hct.fromInt(s.rgb)
            val toneFit = (1.0 - abs(hct.tone - 36.0) / 36.0).coerceIn(0.0, 1.0)
            val chroma = (hct.chroma / 64.0).coerceIn(0.0, 1.0)
            val population = (s.population / maxPop).toDouble()
            val score = toneFit * 0.45 + chroma * 0.25 + population * 0.30
            if (score > bestScore) {
                bestScore = score
                bestRgb = s.rgb
            }
        }
        return bestRgb
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
}
