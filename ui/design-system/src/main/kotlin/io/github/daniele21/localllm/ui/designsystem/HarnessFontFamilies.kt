package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object HarnessFontFamilies {
    val Interface = FontFamily.SansSerif
    val Monospace = FontFamily.Monospace
}

private val MaterialTypography = Typography()

val HarnessTypography = Typography(
    headlineLarge = MaterialTypography.headlineLarge.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = MaterialTypography.titleLarge.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = MaterialTypography.titleMedium.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = MaterialTypography.bodyLarge.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = MaterialTypography.bodyMedium.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = MaterialTypography.labelLarge.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
)
