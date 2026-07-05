package io.github.benderblog.cdripshare.util

object ColorHex {
    private const val DEFAULT_RGB_HEX = "3C3C3C"
    private const val DEFAULT_ARGB = 0xFF3C3C3CL.toInt()
    private val rgbPattern = Regex("^[0-9A-Fa-f]{6}$")

    fun normalizeRgbHex(
        value: String,
        fallback: String = DEFAULT_RGB_HEX,
    ): String {
        val hex = value.removePrefix("#")
        if (rgbPattern.matches(hex)) return hex.uppercase()

        val fallbackHex = fallback.removePrefix("#")
        return if (rgbPattern.matches(fallbackHex)) fallbackHex.uppercase() else DEFAULT_RGB_HEX
    }

    fun parseRgbToArgb(
        value: String,
        fallbackArgb: Int = DEFAULT_ARGB,
    ): Int {
        val hex = value.removePrefix("#")
        return if (rgbPattern.matches(hex)) {
            (0xFF shl 24) or hex.toInt(16)
        } else {
            fallbackArgb
        }
    }
}
