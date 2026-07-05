package io.github.benderblog.cdripshare.util

import io.github.benderblog.cdripshare.model.CoverRenderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverImageGeneratorTest {
    @Test
    fun `layout keeps large shadow bounds inside the output canvas`() {
        val layout = CoverImageGenerator.calculateLayout(
            imageWidth = 1000,
            imageHeight = 1000,
            settings = CoverRenderSettings(
                imageHeight = 1000f,
                shadowBlur = 80f,
                shadowOffsetY = 48f,
                shadowAlpha = 1f,
                usesAdvancedShadow = true,
            ),
        )

        assertTrue(layout.layerBounds.left >= 0f)
        assertTrue(layout.layerBounds.top >= 0f)
        assertTrue(layout.layerBounds.right <= CoverImageGenerator.CANVAS_W)
        assertTrue(layout.layerBounds.bottom <= CoverImageGenerator.CANVAS_H)
        assertTrue(layout.drawH < 1000f)
    }

    @Test
    fun `layout centers the image and shadow envelope instead of the image alone`() {
        val layout = CoverImageGenerator.calculateLayout(
            imageWidth = 1000,
            imageHeight = 1000,
            settings = CoverRenderSettings(
                imageHeight = 800f,
                shadowBlur = 24f,
                shadowOffsetY = 48f,
                shadowAlpha = 1f,
                usesAdvancedShadow = true,
            ),
        )

        assertTrue(layout.drawY < (CoverImageGenerator.CANVAS_H - layout.drawH) / 2f)
        assertEquals(
            layout.layerBounds.top,
            CoverImageGenerator.CANVAS_H - layout.layerBounds.bottom,
            absoluteTolerance = 0.01f,
        )
    }
}
