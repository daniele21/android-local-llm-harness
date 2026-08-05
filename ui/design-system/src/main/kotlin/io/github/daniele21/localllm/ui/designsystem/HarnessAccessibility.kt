package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

const val WcagAaNormalTextContrast = 4.5
const val WcagAaLargeTextContrast = 3.0

fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = relativeLuminance(foreground)
    val backgroundLuminance = relativeLuminance(background)
    return (max(foregroundLuminance, backgroundLuminance) + LuminanceOffset) /
        (min(foregroundLuminance, backgroundLuminance) + LuminanceOffset)
}

fun meetsWcagAa(
    foreground: Color,
    background: Color,
    largeText: Boolean = false,
): Boolean = contrastRatio(foreground, background) >= if (largeText) {
    WcagAaLargeTextContrast
} else {
    WcagAaNormalTextContrast
}

private fun relativeLuminance(color: Color): Double = RedWeight * linearized(color.red) +
    GreenWeight * linearized(color.green) +
    BlueWeight * linearized(color.blue)

private fun linearized(channel: Float): Double {
    val value = channel.toDouble()
    return if (value <= SrgbThreshold) {
        value / SrgbLinearDivisor
    } else {
        ((value + SrgbOffset) / SrgbScale).pow(SrgbExponent)
    }
}

private const val LuminanceOffset = 0.05
private const val SrgbThreshold = 0.04045
private const val SrgbLinearDivisor = 12.92
private const val SrgbOffset = 0.055
private const val SrgbScale = 1.055
private const val SrgbExponent = 2.4
private const val RedWeight = 0.2126
private const val GreenWeight = 0.7152
private const val BlueWeight = 0.0722
