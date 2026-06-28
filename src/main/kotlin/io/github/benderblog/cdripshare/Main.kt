package io.github.benderblog.cdripshare

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.benderblog.cdripshare.ui.App
import io.github.benderblog.cdripshare.viewmodel.MainViewModel

fun main() = application {
    val state = rememberWindowState(width = 1000.dp, height = 750.dp)
    val viewModel = MainViewModel()

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "CDRipShare - 抓轨分享视频生成器"
    ) {
        window.minimumSize = java.awt.Dimension(900, 700)
        App(viewModel)
    }
}
