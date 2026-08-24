package io.github.daniele21.localllm.phonetest

internal enum class HarnessWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

internal data class HarnessAdaptivePolicy(
    val widthClass: HarnessWidthClass,
    val useNavigationRail: Boolean,
    val stackDenseContent: Boolean,
)

internal fun harnessAdaptivePolicy(widthDp: Int, fontScale: Float): HarnessAdaptivePolicy {
    require(widthDp > 0) { "Harness width must be positive" }
    require(fontScale > 0f) { "Harness font scale must be positive" }

    val widthClass = when {
        widthDp < MEDIUM_WIDTH_DP -> HarnessWidthClass.COMPACT
        widthDp < EXPANDED_WIDTH_DP -> HarnessWidthClass.MEDIUM
        else -> HarnessWidthClass.EXPANDED
    }
    return HarnessAdaptivePolicy(
        widthClass = widthClass,
        useNavigationRail = widthClass != HarnessWidthClass.COMPACT,
        stackDenseContent = widthClass == HarnessWidthClass.COMPACT || fontScale >= LARGE_FONT_SCALE,
    )
}

private const val MEDIUM_WIDTH_DP = 600
private const val EXPANDED_WIDTH_DP = 840
private const val LARGE_FONT_SCALE = 1.3f
