package io.github.benderblog.cdripshare.viewmodel

import io.github.benderblog.cdripshare.model.BackgroundMode
import kotlin.test.Test
import kotlin.test.assertEquals

class MainViewModelColorTest {
    @Test
    fun `confirming custom color updates cover background without image`() {
        val viewModel = MainViewModel()

        viewModel.customColorHex = "505A88"
        viewModel.bgMode.value = BackgroundMode.Custom
        viewModel.onBgModeChanged()

        assertEquals(0xFF505A88.toInt(), viewModel.coverBackgroundColor)
    }
}
