package io.github.benderblog.cdripshare.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CoverRenderSettingsTest {
    @Test
    fun `automatic background labels use material palette wording`() {
        assertEquals("自动配色", BackgroundMode.Auto.label)
        assertEquals("封面取色", BackgroundMode.AutoPalette.label)
    }

    @Test
    fun `defaults match current cover rendering`() {
        val settings = CoverRenderSettings()

        assertEquals(800f, settings.imageHeight)
        assertEquals(24f, settings.cornerRadius)
        assertEquals(1f, settings.shadowStrength)
        assertEquals(24f, settings.shadowBlur)
        assertEquals(8f, settings.shadowOffsetY)
        assertEquals(170f / 255f, settings.shadowAlpha)
        assertFalse(settings.usesAdvancedShadow)
    }

    @Test
    fun `normalization clamps all render values`() {
        val settings = CoverRenderSettings(
            imageHeight = 1200f,
            cornerRadius = -4f,
            shadowStrength = 2f,
            shadowBlur = 120f,
            shadowOffsetY = -8f,
            shadowAlpha = 1.4f,
            usesAdvancedShadow = true,
        ).normalized()

        assertEquals(1000f, settings.imageHeight)
        assertEquals(0f, settings.cornerRadius)
        assertEquals(1f, settings.shadowStrength)
        assertEquals(80f, settings.shadowBlur)
        assertEquals(0f, settings.shadowOffsetY)
        assertEquals(1f, settings.shadowAlpha)
        assertEquals(true, settings.usesAdvancedShadow)
    }

    @Test
    fun `shadow strength controls the simple shadow profile`() {
        val settings = CoverRenderSettings().withShadowStrength(0.5f)

        assertEquals(0.5f, settings.shadowStrength)
        assertEquals(12f, settings.shadowBlur)
        assertEquals(4f, settings.shadowOffsetY)
        assertEquals(85f / 255f, settings.shadowAlpha)
        assertFalse(settings.usesAdvancedShadow)
    }

    @Test
    fun `advanced shadow changes keep strength available but mark custom shadow`() {
        val settings = CoverRenderSettings()
            .withShadowBlur(40f)
            .withShadowOffsetY(16f)
            .withShadowAlpha(0.25f)

        assertEquals(1f, settings.shadowStrength)
        assertEquals(40f, settings.shadowBlur)
        assertEquals(16f, settings.shadowOffsetY)
        assertEquals(0.25f, settings.shadowAlpha)
        assertEquals(true, settings.usesAdvancedShadow)
    }
}
