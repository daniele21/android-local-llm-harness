package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

const val WCAG_AA_NORMAL_TEXT_CONTRAST = 4.5
const val WCAG_AA_LARGE_TEXT_CONTRAST = 3.0

fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = relativeLuminance(foreground)
    val backgroundLuminance = relativeLuminance(background)
    return (max(foregroundLuminance, backgroundLuminance) + LUMINANCE_OFFSET) /
        (min(foregroundLuminance, backgroundLuminance) + LUMINANCE_OFFSET)
}

fun meetsWcagAa(foreground: Color, background: Color, largeText: Boolean = false): Boolean =
    contrastRatio(foreground, background) >= if (largeText) {
        WCAG_AA_LARGE_TEXT_CONTRAST
    } else {
        WCAG_AA_NORMAL_TEXT_CONTRAST
    }

private fun relativeLuminance(color: Color): Double = RED_WEIGHT * linearized(color.red) +
    GREEN_WEIGHT * linearized(color.green) +
    BLUE_WEIGHT * linearized(color.blue)

private fun linearized(channel: Float): Double {
    val value = channel.toDouble()
    return if (value <= SRGB_THRESHOLD) {
        value / SRGB_LINEAR_DIVISOR
    } else {
        ((value + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_EXPONENT)
    }
}

private const val LUMINANCE_OFFSET = 0.05
private const val SRGB_THRESHOLD = 0.04045
private const val SRGB_LINEAR_DIVISOR = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_EXPONENT = 2.4
private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722
