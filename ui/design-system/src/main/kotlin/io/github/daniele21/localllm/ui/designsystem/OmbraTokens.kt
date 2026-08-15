package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Deterministic offline fallback until bundled Manrope/Inter licensing and APK impact are reviewed. */
object OmbraFontFamilies {
    val Heading = FontFamily.SansSerif
    val Interface = FontFamily.SansSerif
    val Placeholder = FontFamily.Monospace
}

private val MaterialTypography = Typography()

val OmbraTypography =
    Typography(
        headlineLarge =
        MaterialTypography.headlineLarge.copy(
            fontFamily = OmbraFontFamilies.Heading,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
        ),
        headlineMedium =
        MaterialTypography.headlineMedium.copy(
            fontFamily = OmbraFontFamilies.Heading,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        titleLarge =
        MaterialTypography.titleLarge.copy(
            fontFamily = OmbraFontFamilies.Heading,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        ),
        titleMedium =
        MaterialTypography.titleMedium.copy(
            fontFamily = OmbraFontFamilies.Interface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        bodyLarge =
        MaterialTypography.bodyLarge.copy(
            fontFamily = OmbraFontFamilies.Interface,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium =
        MaterialTypography.bodyMedium.copy(
            fontFamily = OmbraFontFamilies.Interface,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelLarge =
        MaterialTypography.labelLarge.copy(
            fontFamily = OmbraFontFamilies.Interface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelMedium =
        MaterialTypography.labelMedium.copy(
            fontFamily = OmbraFontFamilies.Interface,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
    )

val OmbraShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )

@Immutable
data class OmbraSpacing(
    val xxs: Dp,
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    val minimumTouchTarget: Dp,
)

internal val DefaultOmbraSpacing =
    OmbraSpacing(
        xxs = 4.dp,
        xs = 8.dp,
        sm = 12.dp,
        md = 16.dp,
        lg = 24.dp,
        xl = 32.dp,
        xxl = 48.dp,
        minimumTouchTarget = 48.dp,
    )

val LocalOmbraSpacing = staticCompositionLocalOf { DefaultOmbraSpacing }
