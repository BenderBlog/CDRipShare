package io.github.benderblog.cdripshare.util

import com.materialkolor.hct.Hct
import io.github.benderblog.cdripshare.model.BackgroundMode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CoverColorExtractorTest {
    @Test
    fun `material palette derives role colors from a seed`() {
        val palette = CoverMaterialPalette.fromSeed(0xFFCC6633.toInt())

        assertEquals(0xFFCC6633.toInt(), palette.seed)
        assertTrue(palette.roleColors.all { ((it ushr 24) and 0xFF) == 0xFF })
        assertNotEquals(palette.seed, palette.background)
        assertNotEquals(palette.background, palette.primary)
    }

    @Test
    fun `cover background uses a visible material tone instead of the app background role`() {
        val palette = CoverMaterialPalette.fromSeed(0xFFCC6633.toInt())
        val coverTone = Hct.fromInt(palette.coverBackground).tone

        assertNotEquals(palette.background, palette.coverBackground)
        assertTrue(coverTone in 38.0..50.0, "cover tone was $coverTone")
    }

    @Test
    fun `extract returns reusable color decisions for a cover image`() {
        val imageFile = createTwoTonePng()

        val colors = CoverColorExtractor.extract(imageFile)

        assertTrue(colors.swatches.isNotEmpty())
        assertNotEquals(0, colors.dominant)
        assertNotEquals(0, colors.autoSeed)
        assertNotEquals(0, colors.paletteSeed)
        assertNotEquals(0, colors.autoBackground)
        assertNotEquals(0, colors.paletteBackground)
        assertEquals(colors.autoSeed, colors.autoMaterialPalette.seed)
        assertEquals(colors.paletteSeed, colors.paletteMaterialPalette.seed)
        assertEquals(colors.autoMaterialPalette.coverBackground, colors.autoBackground)
        assertEquals(colors.paletteMaterialPalette.coverBackground, colors.paletteBackground)
        assertEquals(
            colors.autoBackground,
            CoverColorExtractor.resolveBackgroundColor(imageFile, BackgroundMode.Auto)
        )
        assertEquals(
            colors.paletteBackground,
            CoverColorExtractor.resolveBackgroundColor(imageFile, BackgroundMode.AutoPalette)
        )
    }

    @Test
    fun `solid and custom background modes bypass image extraction`() {
        assertEquals(
            0xFF112233.toInt(),
            CoverColorExtractor.resolveSolidBackgroundColor(BackgroundMode.Custom, 0xFF112233.toInt())
        )
        assertEquals(
            BackgroundMode.Navy.colorHex,
            CoverColorExtractor.resolveSolidBackgroundColor(BackgroundMode.Navy)
        )
    }

    private fun createTwoTonePng(): File {
        val file = File.createTempFile("cover-color-extractor-", ".png")
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(24, 12))
        surface.canvas.clear(0xFF203040.toInt())
        Paint().use { paint ->
            paint.color = 0xFFCC6633.toInt()
            surface.canvas.drawRect(Rect(0f, 0f, 8f, 12f), paint)
        }
        val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)
            ?: error("PNG encode failed")
        file.writeBytes(data.bytes)
        file.deleteOnExit()
        return file
    }
}
