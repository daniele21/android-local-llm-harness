package io.github.daniele21.localllm.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraAccessibilityTest {
    @Test
    fun `light material role pairs meet WCAG AA`() {
        assertMaterialPairsMeetAa(OmbraLightColorScheme)
    }

    @Test
    fun `derived dark material role pairs meet WCAG AA`() {
        assertMaterialPairsMeetAa(OmbraDarkColorScheme)
    }

    @Test
    fun `all semantic status pairs meet WCAG AA`() {
        listOf(OmbraLightStatusColors, OmbraDarkStatusColors).forEach { colors ->
            OmbraStatusTone.entries.forEach { tone ->
                assertTrue(
                    "Expected $tone OMBRA status pair to meet WCAG AA",
                    meetsWcagAa(colors.content(tone), colors.container(tone)),
                )
            }
        }
    }

    @Test
    fun `minimum interactive target follows 48 dp contract`() {
        assertTrue(DefaultOmbraSpacing.minimumTouchTarget.value >= 48f)
    }

    @Test
    fun `spacing rhythm matches reviewed OMBRA contract`() {
        assertEquals(
            listOf(4f, 8f, 12f, 16f, 24f, 32f, 48f),
            listOf(
                DefaultOmbraSpacing.xxs.value,
                DefaultOmbraSpacing.xs.value,
                DefaultOmbraSpacing.sm.value,
                DefaultOmbraSpacing.md.value,
                DefaultOmbraSpacing.lg.value,
                DefaultOmbraSpacing.xl.value,
                DefaultOmbraSpacing.xxl.value,
            ),
        )
    }

    private fun assertMaterialPairsMeetAa(colors: androidx.compose.material3.ColorScheme) {
        assertTrue(meetsWcagAa(colors.onBackground, colors.background))
        assertTrue(meetsWcagAa(colors.onSurface, colors.surface))
        assertTrue(meetsWcagAa(colors.onSurfaceVariant, colors.surfaceVariant))
        assertTrue(meetsWcagAa(colors.onPrimary, colors.primary))
        assertTrue(meetsWcagAa(colors.onPrimaryContainer, colors.primaryContainer))
        assertTrue(meetsWcagAa(colors.onSecondary, colors.secondary))
        assertTrue(meetsWcagAa(colors.onSecondaryContainer, colors.secondaryContainer))
        assertTrue(meetsWcagAa(colors.onError, colors.error))
        assertTrue(meetsWcagAa(colors.onErrorContainer, colors.errorContainer))
    }
}
