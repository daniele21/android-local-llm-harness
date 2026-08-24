package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessAdaptivePolicyTest {
    @Test
    fun compactUsesBottomNavigationAndStacksDenseContent() {
        val policy = harnessAdaptivePolicy(widthDp = 599, fontScale = 1f)

        assertEquals(HarnessWidthClass.COMPACT, policy.widthClass)
        assertFalse(policy.useNavigationRail)
        assertTrue(policy.stackDenseContent)
    }

    @Test
    fun mediumUsesRailWithoutForcingDenseContentToStack() {
        val policy = harnessAdaptivePolicy(widthDp = 600, fontScale = 1f)

        assertEquals(HarnessWidthClass.MEDIUM, policy.widthClass)
        assertTrue(policy.useNavigationRail)
        assertFalse(policy.stackDenseContent)
    }

    @Test
    fun expandedUsesRailAndLargeFontStillRequestsReflow() {
        val policy = harnessAdaptivePolicy(widthDp = 840, fontScale = 1.3f)

        assertEquals(HarnessWidthClass.EXPANDED, policy.widthClass)
        assertTrue(policy.useNavigationRail)
        assertTrue(policy.stackDenseContent)
    }
}
