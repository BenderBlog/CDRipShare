package io.github.benderblog.cdripshare.model

import java.io.File

data class AudioItem(
    val file: File,
    val codec: String,
    val durationSec: Double,
    val displayName: String = file.name,
)
