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
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = MaterialTypography.headlineMedium.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = MaterialTypography.titleLarge.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = MaterialTypography.titleMedium.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = MaterialTypography.bodyLarge.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = MaterialTypography.bodyMedium.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = MaterialTypography.labelLarge.copy(
        fontFamily = HarnessFontFamilies.Interface,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)
