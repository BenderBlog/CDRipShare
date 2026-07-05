package io.github.benderblog.cdripshare.util

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorCutQuantizerTest {
    @Test
    fun `solid color swatch expands quantized channels back to 8 bit`() {
        val sourceColor = 0xFFAA5533.toInt()
        val imageFile = createSolidPng(sourceColor)

        val swatches = ColorCutQuantizer.quantize(imageFile, maxColors = 16)

        assertEquals(1, swatches.size)
        val swatch = swatches.single()
        assertTrue(abs(swatch.red - 170) <= 4, "red should stay close to source, was ${swatch.red}")
        assertTrue(abs(swatch.green - 85) <= 4, "green should stay close to source, was ${swatch.green}")
        assertTrue(abs(swatch.blue - 51) <= 4, "blue should stay close to source, was ${swatch.blue}")
        assertEquals(0xFF000000.toInt(), swatch.rgb and 0xFF000000.toInt())
    }

    private fun createSolidPng(color: Int): File {
        val file = File.createTempFile("quantizer-solid-", ".png")
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(8, 8))
        surface.canvas.clear(color)
        val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)
            ?: error("PNG encode failed")
        file.writeBytes(data.bytes)
        file.deleteOnExit()
        return file
    }
}
