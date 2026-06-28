package io.github.benderblog.cdripshare.model

import java.io.File

enum class Phase { Idle, Working, Error }

enum class UpsampleMode(val label: String, val sampleRate: Int, val bitDepth: Int) {
    NONE("不升频（保持原样）", 0, 0),
    SR48K_24B("48kHz / 24bit", 48000, 24),
    SR96K_24B("96kHz / 24bit", 96000, 24),
}

data class AppState(
    val phase: Phase = Phase.Idle,
    val progress: Float = 0f,
    val phaseLabel: String = "",
    val errorMessage: String? = null,
    val errorDetail: String? = null,
    val outputFile: File? = null,
)
