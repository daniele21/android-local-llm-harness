package io.github.daniele21.localllm.runtime

internal object ContextSizeSelector {
    val supportedSizes: List<Int> = listOf(1_024, 2_048, 4_096, 8_192, 16_384, 32_768, 65_536)

    fun selectAuto(required: Int, maximum: Int, preferredMinimum: Int?): Int? {
        if (required <= 0 || maximum <= 0 || required > maximum) return null
        val target = maxOf(required, preferredMinimum?.coerceAtMost(maximum) ?: required)
        return supportedSizes.firstOrNull { it >= target && it <= maximum }
    }

    fun supportsManual(tokens: Int, required: Int, maximum: Int): Boolean =
        tokens in supportedSizes && tokens >= required && tokens <= maximum
}
