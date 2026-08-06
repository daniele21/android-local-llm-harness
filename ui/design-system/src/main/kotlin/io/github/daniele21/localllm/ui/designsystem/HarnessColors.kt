package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object HarnessColors {
    val Background = Color(0xFF080C13)
    val Surface = Color(0xFF0F1520)
    val SurfaceElevated = Color(0xFF141B27)
    val Primary = Color(0xFF7555F6)
    val OnPrimary = Color.White
    val PrimaryContainer = Color(0xFF241C4D)
    val OnPrimaryContainer = Color(0xFFF5F1FF)
    val Secondary = Color(0xFF27D3AE)
    val OnSecondary = Color(0xFF031C17)
    val SecondaryContainer = Color(0xFF0B302C)
    val OnSecondaryContainer = Color(0xFFD8FFF5)
    val TextPrimary = Color(0xFFF4F2FA)
    val TextSecondary = Color(0xFF8C93A7)
    val Outline = Color(0xFF242D3C)
    val Success = Color(0xFF38C172)
    val Warning = Color(0xFFF4B740)
    val Error = Color(0xFFEF5B5B)
}

internal object HarnessLightColors {
    val Primary = Color(0xFF5A3ED6)
    val OnPrimary = Color.White
    val PrimaryContainer = Color(0xFFE7E0FF)
    val OnPrimaryContainer = Color(0xFF21105F)
    val Secondary = Color(0xFF007D69)
    val OnSecondary = Color.White
    val SecondaryContainer = Color(0xFFB9F2E4)
    val OnSecondaryContainer = Color(0xFF002019)
    val Background = Color(0xFFF7F8FA)
    val Surface = Color.White
    val SurfaceElevated = Color(0xFFEDF0F4)
    val TextPrimary = Color(0xFF11151B)
    val TextSecondary = Color(0xFF596273)
    val Outline = Color(0xFFC6CCD5)
    val Error = Color(0xFFBA1A1A)
}

internal val HarnessDarkColorScheme = darkColorScheme(
    primary = HarnessColors.Primary,
    onPrimary = HarnessColors.OnPrimary,
    primaryContainer = HarnessColors.PrimaryContainer,
    onPrimaryContainer = HarnessColors.OnPrimaryContainer,
    secondary = HarnessColors.Secondary,
    onSecondary = HarnessColors.OnSecondary,
    secondaryContainer = HarnessColors.SecondaryContainer,
    onSecondaryContainer = HarnessColors.OnSecondaryContainer,
    background = HarnessColors.Background,
    surface = HarnessColors.Surface,
    surfaceVariant = HarnessColors.SurfaceElevated,
    onBackground = HarnessColors.TextPrimary,
    onSurface = HarnessColors.TextPrimary,
    onSurfaceVariant = HarnessColors.TextSecondary,
    outline = HarnessColors.Outline,
    error = HarnessColors.Error,
    onError = Color.Black,
)

internal val HarnessLightColorScheme = lightColorScheme(
    primary = HarnessLightColors.Primary,
    onPrimary = HarnessLightColors.OnPrimary,
    primaryContainer = HarnessLightColors.PrimaryContainer,
    onPrimaryContainer = HarnessLightColors.OnPrimaryContainer,
    secondary = HarnessLightColors.Secondary,
    onSecondary = HarnessLightColors.OnSecondary,
    secondaryContainer = HarnessLightColors.SecondaryContainer,
    onSecondaryContainer = HarnessLightColors.OnSecondaryContainer,
    background = HarnessLightColors.Background,
    surface = HarnessLightColors.Surface,
    surfaceVariant = HarnessLightColors.SurfaceElevated,
    onBackground = HarnessLightColors.TextPrimary,
    onSurface = HarnessLightColors.TextPrimary,
    onSurfaceVariant = HarnessLightColors.TextSecondary,
    outline = HarnessLightColors.Outline,
    error = HarnessLightColors.Error,
    onError = Color.White,
)

enum class HarnessStatusTone {
    NEUTRAL,
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

@Immutable
data class HarnessStatusColors(
    val neutralContainer: Color,
    val neutralContent: Color,
    val infoContainer: Color,
    val infoContent: Color,
    val successContainer: Color,
    val successContent: Color,
    val warningContainer: Color,
    val warningContent: Color,
    val errorContainer: Color,
    val errorContent: Color,
) {
    fun container(tone: HarnessStatusTone): Color = when (tone) {
        HarnessStatusTone.NEUTRAL -> neutralContainer
        HarnessStatusTone.INFO -> infoContainer
        HarnessStatusTone.SUCCESS -> successContainer
        HarnessStatusTone.WARNING -> warningContainer
        HarnessStatusTone.ERROR -> errorContainer
    }

    fun content(tone: HarnessStatusTone): Color = when (tone) {
        HarnessStatusTone.NEUTRAL -> neutralContent
        HarnessStatusTone.INFO -> infoContent
        HarnessStatusTone.SUCCESS -> successContent
        HarnessStatusTone.WARNING -> warningContent
        HarnessStatusTone.ERROR -> errorContent
    }
}

internal val HarnessDarkStatusColors = HarnessStatusColors(
    neutralContainer = HarnessColors.SurfaceElevated,
    neutralContent = HarnessColors.TextSecondary,
    infoContainer = HarnessColors.PrimaryContainer,
    infoContent = HarnessColors.OnPrimaryContainer,
    successContainer = Color(0xFF103D2A),
    successContent = Color(0xFF8DE9B3),
    warningContainer = Color(0xFF4A3500),
    warningContent = Color(0xFFFFD67A),
    errorContainer = Color(0xFF5C1F22),
    errorContent = Color(0xFFFFB3B3),
)

internal val HarnessLightStatusColors = HarnessStatusColors(
    neutralContainer = HarnessLightColors.SurfaceElevated,
    neutralContent = Color(0xFF3F4856),
    infoContainer = HarnessLightColors.PrimaryContainer,
    infoContent = Color(0xFF3D278D),
    successContainer = Color(0xFFC8F4D8),
    successContent = Color(0xFF0B5D34),
    warningContainer = Color(0xFFFFE7B0),
    warningContent = Color(0xFF664000),
    errorContainer = Color(0xFFFFDAD6),
    errorContent = Color(0xFF93000A),
)

val LocalHarnessStatusColors = staticCompositionLocalOf { HarnessDarkStatusColors }
