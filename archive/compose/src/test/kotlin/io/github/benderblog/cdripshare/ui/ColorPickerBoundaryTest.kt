package io.github.benderblog.cdripshare.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test

class ColorPickerBoundaryTest {
    @Test
    fun `hsv color construction accepts hue slider endpoint`() {
        Color.hsv(360f, 1f, 1f)
    }
}
