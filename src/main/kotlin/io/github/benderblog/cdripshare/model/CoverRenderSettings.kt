package io.github.benderblog.cdripshare.model

data class CoverRenderSettings(
    val imageHeight: Float = DEFAULT_IMAGE_HEIGHT,
    val cornerRadius: Float = DEFAULT_CORNER_RADIUS,
    val shadowStrength: Float = DEFAULT_SHADOW_STRENGTH,
    val shadowBlur: Float = DEFAULT_SHADOW_BLUR,
    val shadowOffsetY: Float = DEFAULT_SHADOW_OFFSET_Y,
    val shadowAlpha: Float = DEFAULT_SHADOW_ALPHA,
    val usesAdvancedShadow: Boolean = false,
) {
    fun normalized(): CoverRenderSettings = copy(
        imageHeight = imageHeight.coerceIn(MIN_IMAGE_HEIGHT, MAX_IMAGE_HEIGHT),
        cornerRadius = cornerRadius.coerceIn(MIN_CORNER_RADIUS, MAX_CORNER_RADIUS),
        shadowStrength = shadowStrength.coerceIn(MIN_SHADOW_STRENGTH, MAX_SHADOW_STRENGTH),
        shadowBlur = shadowBlur.coerceIn(MIN_SHADOW_BLUR, MAX_SHADOW_BLUR),
        shadowOffsetY = shadowOffsetY.coerceIn(MIN_SHADOW_OFFSET_Y, MAX_SHADOW_OFFSET_Y),
        shadowAlpha = shadowAlpha.coerceIn(MIN_SHADOW_ALPHA, MAX_SHADOW_ALPHA),
    )

    fun withImageHeight(value: Float): CoverRenderSettings =
        copy(imageHeight = value).normalized()

    fun withCornerRadius(value: Float): CoverRenderSettings =
        copy(cornerRadius = value).normalized()

    fun withShadowStrength(value: Float): CoverRenderSettings {
        val strength = value.coerceIn(MIN_SHADOW_STRENGTH, MAX_SHADOW_STRENGTH)
        return copy(
            shadowStrength = strength,
            shadowBlur = DEFAULT_SHADOW_BLUR * strength,
            shadowOffsetY = DEFAULT_SHADOW_OFFSET_Y * strength,
            shadowAlpha = DEFAULT_SHADOW_ALPHA * strength,
            usesAdvancedShadow = false,
        ).normalized()
    }

    fun withShadowBlur(value: Float): CoverRenderSettings =
        copy(shadowBlur = value, usesAdvancedShadow = true).normalized()

    fun withShadowOffsetY(value: Float): CoverRenderSettings =
        copy(shadowOffsetY = value, usesAdvancedShadow = true).normalized()

    fun withShadowAlpha(value: Float): CoverRenderSettings =
        copy(shadowAlpha = value, usesAdvancedShadow = true).normalized()

    companion object {
        const val MIN_IMAGE_HEIGHT = 800f
        const val MAX_IMAGE_HEIGHT = 1000f
        const val DEFAULT_IMAGE_HEIGHT = 800f

        const val MIN_CORNER_RADIUS = 0f
        const val MAX_CORNER_RADIUS = 80f
        const val DEFAULT_CORNER_RADIUS = 24f

        const val MIN_SHADOW_STRENGTH = 0f
        const val MAX_SHADOW_STRENGTH = 1f
        const val DEFAULT_SHADOW_STRENGTH = 1f

        const val MIN_SHADOW_BLUR = 0f
        const val MAX_SHADOW_BLUR = 80f
        const val DEFAULT_SHADOW_BLUR = 24f

        const val MIN_SHADOW_OFFSET_Y = 0f
        const val MAX_SHADOW_OFFSET_Y = 48f
        const val DEFAULT_SHADOW_OFFSET_Y = 8f

        const val MIN_SHADOW_ALPHA = 0f
        const val MAX_SHADOW_ALPHA = 1f
        const val DEFAULT_SHADOW_ALPHA = 170f / 255f
    }
}
