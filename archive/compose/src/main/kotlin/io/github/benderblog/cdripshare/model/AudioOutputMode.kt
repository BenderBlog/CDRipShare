package io.github.benderblog.cdripshare.model

enum class AudioOutputMode(val label: String) {
    Passthrough("不升频（保持原样）"),
    HiRes48("48kHz / 24bit"),
    HiRes96("96kHz / 24bit"),
    AAC256("AAC 256kbps"),
    AAC192("AAC 192kbps"),
}
