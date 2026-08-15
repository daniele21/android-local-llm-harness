package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** OMBRA brand reference tokens. Screens consume Material/OMBRA semantic roles, never these values directly. */
object OmbraColors {
    val Ink = Color(0xFF15201D)
    val LocalMoss = Color(0xFF315C4F)
    val SignalMint = Color(0xFF65D6A6)
    val Paper = Color(0xFFF6F4EE)
    val SoftSage = Color(0xFFDDE9E2)
    val ReviewAmber = Color(0xFFE6A94A)
    val ErrorCoral = Color(0xFFD8655B)
}

internal object OmbraDarkColors {
    val Background = Color(0xFF101714)
    val Surface = Color(0xFF16201C)
    val SurfaceVariant = Color(0xFF20302A)
    val PrimaryContainer = Color(0xFF25473D)
    val OnPrimaryContainer = Color(0xFFDDF7EC)
    val Secondary = Color(0xFFA9CDBD)
    val SecondaryContainer = Color(0xFF314B40)
    val OnSecondaryContainer = Color(0xFFE2F1EA)
    val TextPrimary = Color(0xFFEEF4F0)
    val TextSecondary = Color(0xFFC4D5CD)
    val Outline = Color(0xFF83978E)
    val Error = Color(0xFFFFB4AB)
    val OnError = Color(0xFF5F1412)
    val Review = Color(0xFFF3C36C)
    val ReviewContainer = Color(0xFF2A4138)
}

val OmbraLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = OmbraColors.LocalMoss,
        onPrimary = Color.White,
        primaryContainer = OmbraColors.SoftSage,
        onPrimaryContainer = OmbraColors.Ink,
        secondary = OmbraColors.SignalMint,
        onSecondary = OmbraColors.Ink,
        secondaryContainer = Color(0xFFC9F2DF),
        onSecondaryContainer = OmbraColors.Ink,
        background = OmbraColors.Paper,
        onBackground = OmbraColors.Ink,
        surface = Color(0xFFFFFDF8),
        onSurface = OmbraColors.Ink,
        surfaceVariant = OmbraColors.SoftSage,
        onSurfaceVariant = OmbraColors.Ink,
        outline = Color(0xFF687A72),
        error = OmbraColors.ErrorCoral,
        onError = OmbraColors.Ink,
        errorContainer = Color(0xFFFFDAD5),
        onErrorContainer = Color(0xFF54110E),
    )

val OmbraDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = OmbraColors.SignalMint,
        onPrimary = OmbraColors.Ink,
        primaryContainer = OmbraDarkColors.PrimaryContainer,
        onPrimaryContainer = OmbraDarkColors.OnPrimaryContainer,
        secondary = OmbraDarkColors.Secondary,
        onSecondary = OmbraColors.Ink,
        secondaryContainer = OmbraDarkColors.SecondaryContainer,
        onSecondaryContainer = OmbraDarkColors.OnSecondaryContainer,
        background = OmbraDarkColors.Background,
        onBackground = OmbraDarkColors.TextPrimary,
        surface = OmbraDarkColors.Surface,
        onSurface = OmbraDarkColors.TextPrimary,
        surfaceVariant = OmbraDarkColors.SurfaceVariant,
        onSurfaceVariant = OmbraDarkColors.TextSecondary,
        outline = OmbraDarkColors.Outline,
        error = OmbraDarkColors.Error,
        onError = OmbraDarkColors.OnError,
        errorContainer = Color(0xFF5F1412),
        onErrorContainer = Color(0xFFFFDAD5),
    )

enum class OmbraStatusTone {
    NEUTRAL,
    LOCAL_READY,
    REVIEW,
    ERROR,
}

@Immutable
data class OmbraStatusColors(
    val neutralContainer: Color,
    val neutralContent: Color,
    val localReadyContainer: Color,
    val localReadyContent: Color,
    val reviewContainer: Color,
    val reviewContent: Color,
    val errorContainer: Color,
    val errorContent: Color,
) {
    fun container(tone: OmbraStatusTone): Color = when (tone) {
        OmbraStatusTone.NEUTRAL -> neutralContainer
        OmbraStatusTone.LOCAL_READY -> localReadyContainer
        OmbraStatusTone.REVIEW -> reviewContainer
        OmbraStatusTone.ERROR -> errorContainer
    }

    fun content(tone: OmbraStatusTone): Color = when (tone) {
        OmbraStatusTone.NEUTRAL -> neutralContent
        OmbraStatusTone.LOCAL_READY -> localReadyContent
        OmbraStatusTone.REVIEW -> reviewContent
        OmbraStatusTone.ERROR -> errorContent
    }
}

internal val OmbraLightStatusColors =
    OmbraStatusColors(
        neutralContainer = OmbraColors.SoftSage,
        neutralContent = OmbraColors.Ink,
        localReadyContainer = Color(0xFFC9F2DF),
        localReadyContent = OmbraColors.Ink,
        reviewContainer = OmbraColors.ReviewAmber,
        reviewContent = OmbraColors.Ink,
        errorContainer = OmbraColors.ErrorCoral,
        errorContent = OmbraColors.Ink,
    )

internal val OmbraDarkStatusColors =
    OmbraStatusColors(
        neutralContainer = OmbraDarkColors.SurfaceVariant,
        neutralContent = OmbraDarkColors.TextSecondary,
        localReadyContainer = OmbraDarkColors.PrimaryContainer,
        localReadyContent = OmbraDarkColors.OnPrimaryContainer,
        reviewContainer = OmbraDarkColors.Review,
        reviewContent = OmbraColors.Ink,
        errorContainer = OmbraDarkColors.Error,
        errorContent = OmbraDarkColors.OnError,
    )

val LocalOmbraStatusColors = staticCompositionLocalOf { OmbraLightStatusColors }
