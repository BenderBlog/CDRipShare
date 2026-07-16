package io.github.benderblog.cdripshare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.benderblog.cdripshare.model.Phase

@Composable
fun ControlPanel(
    phase: Phase,
    progress: Float,
    phaseLabel: String,
    errorMessage: String?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (phase) {
                Phase.Idle -> {
                    Button(onClick = onStart) {
                        Text("开始合成")
                    }
                }
                Phase.Working -> {
                    OutlinedButton(onClick = onCancel) {
                        Text("取消")
                    }
                }
                Phase.Error -> {
                    Button(onClick = onRetry) {
                        Text("重试")
                    }
                    OutlinedButton(onClick = onClear) {
                        Text("清除")
                    }
                }
            }
        }

        if (phase == Phase.Working || (phase == Phase.Idle && progress > 0f)) {
            Column {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (phase == Phase.Error)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                if (phaseLabel.isNotEmpty()) {
                    Text(
                        phaseLabel,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (phase == Phase.Error && errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    errorMessage,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
