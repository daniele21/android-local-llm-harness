package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessAccessibilityTest {
    @Test
    fun `dark palette text and interactive fills meet WCAG AA`() {
        val colors = harnessColorScheme(darkTheme = true)

        assertTrue(meetsWcagAa(colors.onBackground, colors.background))
        assertTrue(meetsWcagAa(colors.onSurfaceVariant, colors.surface))
        assertTrue(meetsWcagAa(colors.onPrimary, colors.primary))
        assertTrue(meetsWcagAa(colors.onSecondary, colors.secondary))
    }

    @Test
    fun `light palette text and interactive fills meet WCAG AA`() {
        val colors = harnessColorScheme(darkTheme = false)

        assertTrue(meetsWcagAa(colors.onBackground, colors.background))
        assertTrue(meetsWcagAa(colors.onSurfaceVariant, colors.surface))
        assertTrue(meetsWcagAa(colors.onPrimary, colors.primary))
        assertTrue(meetsWcagAa(colors.onSecondary, colors.secondary))
    }

    @Test
    fun `semantic status pairs meet WCAG AA`() {
        listOf(HarnessDarkStatusColors, HarnessLightStatusColors).forEach { colors ->
            HarnessStatusTone.entries.forEach { tone ->
                assertTrue(
                    "Expected $tone status colors to meet WCAG AA",
                    meetsWcagAa(colors.content(tone), colors.container(tone)),
                )
            }
        }
    }

    @Test
    fun `minimum touch target is at least 48 dp`() {
        assertTrue(HarnessMinimumTouchTarget.value >= MINIMUM_TOUCH_TARGET_DP)
    }

    @Test
    fun `contrast helper recognizes failing pair`() {
        assertTrue(!meetsWcagAa(Color.White, Color(0xFFBDBDBD)))
    }

    private companion object {
        const val MINIMUM_TOUCH_TARGET_DP = 48f
    }
}
