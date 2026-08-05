@file:Suppress("FunctionName")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun HarnessTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalHarnessSpacing provides DefaultHarnessSpacing,
        LocalHarnessStatusColors provides
        if (darkTheme) {
            HarnessDarkStatusColors
        } else {
            HarnessLightStatusColors
        },
    ) {
        MaterialTheme(
            colorScheme = harnessColorScheme(darkTheme),
            typography = HarnessTypography,
            shapes = HarnessShapes,
            content = content,
        )
    }
}

fun harnessColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        HarnessDarkColorScheme
    } else {
        HarnessLightColorScheme
    }
