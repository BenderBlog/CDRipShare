package io.github.benderblog.cdripshare.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.benderblog.cdripshare.model.BackgroundMode
import io.github.benderblog.cdripshare.model.Phase
import io.github.benderblog.cdripshare.viewmodel.MainViewModel

@Composable
fun CoverEditorWindow(
    viewModel: MainViewModel,
    onSelectImage: () -> Unit,
) {
    val state = viewModel.appState.value
    val isWorking = state.phase == Phase.Working

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            CoverEditorContent(
                imageFile = viewModel.imageFile.value,
                coverPreview = viewModel.coverPreview.value,
                onSelect = onSelectImage,
                enabled = !isWorking,
                bgMode = viewModel.bgMode.value,
                onBgModeChange = remember(viewModel) {
                    { mode: BackgroundMode ->
                        viewModel.bgMode.value = mode
                        viewModel.onBgModeChanged()
                    }
                },
                customColorHex = viewModel.customColorHex,
                onCustomColorConfirm = remember(viewModel) {
                    { hex: String ->
                        viewModel.customColorHex = hex
                        viewModel.bgMode.value = BackgroundMode.Custom
                        viewModel.onBgModeChanged()
                    }
                }
            )
        }
    }
}
