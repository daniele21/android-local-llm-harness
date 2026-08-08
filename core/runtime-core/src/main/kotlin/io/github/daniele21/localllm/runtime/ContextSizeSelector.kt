package io.github.daniele21.localllm.runtime

internal object ContextSizeSelector {
    val supportedSizes: List<Int> = listOf(1_024, 2_048, 4_096, 8_192, 16_384, 32_768, 65_536)

    fun selectAuto(required: Int, maximum: Int, preferredMinimum: Int?, candidateSizes: List<Int> = supportedSizes): Int? {
        if (required <= 0 || maximum <= 0 || required > maximum) return null
        val sizes = candidateSizes.distinct().sorted().filter { it > 0 }
        val target = maxOf(required, preferredMinimum?.coerceAtMost(maximum) ?: required)
        return sizes.firstOrNull { it >= target && it <= maximum }
    }

    fun supportsManual(tokens: Int, required: Int, maximum: Int, candidateSizes: List<Int> = supportedSizes): Boolean =
        tokens in candidateSizes && tokens >= required && tokens <= maximum
}
