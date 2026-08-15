@file:Suppress("FunctionName")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun OmbraTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalOmbraSpacing provides DefaultOmbraSpacing,
        LocalOmbraStatusColors provides
            if (darkTheme) {
                OmbraDarkStatusColors
            } else {
                OmbraLightStatusColors
            },
    ) {
        MaterialTheme(
            colorScheme = ombraColorScheme(darkTheme),
            typography = OmbraTypography,
            shapes = OmbraShapes,
            content = content,
        )
    }
}

fun ombraColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        OmbraDarkColorScheme
    } else {
        OmbraLightColorScheme
    }
