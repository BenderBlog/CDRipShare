package io.github.benderblog.cdripshare

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.benderblog.cdripshare.ui.App
import io.github.benderblog.cdripshare.ui.CoverEditorWindow
import io.github.benderblog.cdripshare.ui.pickCoverImage
import io.github.benderblog.cdripshare.viewmodel.MainViewModel

fun main() = application {
    val state = rememberWindowState(width = 1000.dp, height = 750.dp)
    val coverEditorState = rememberWindowState(width = 860.dp, height = 640.dp)
    val viewModel = remember { MainViewModel() }
    var showCoverEditor by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "CDRipShare - 抓轨分享视频生成器"
    ) {
        window.minimumSize = java.awt.Dimension(900, 700)
        App(
            viewModel = viewModel,
            onOpenCoverEditor = { showCoverEditor = true }
        )
    }

    if (showCoverEditor) {
        Window(
            onCloseRequest = { showCoverEditor = false },
            state = coverEditorState,
            title = "编辑封面"
        ) {
            window.minimumSize = java.awt.Dimension(760, 540)
            CoverEditorWindow(
                viewModel = viewModel,
                onSelectImage = { pickCoverImage(viewModel) }
            )
        }
    }
}
