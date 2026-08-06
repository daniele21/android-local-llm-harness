package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class HarnessSpacing(
    val xSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val xLarge: Dp = 24.dp,
    val xxLarge: Dp = 32.dp,
)

val HarnessMinimumTouchTarget = 48.dp
internal val DefaultHarnessSpacing = HarnessSpacing()
val LocalHarnessSpacing = staticCompositionLocalOf { DefaultHarnessSpacing }
