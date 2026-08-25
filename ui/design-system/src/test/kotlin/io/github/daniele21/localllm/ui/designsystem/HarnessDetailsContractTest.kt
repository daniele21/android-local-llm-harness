package io.github.daniele21.localllm.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessDetailsContractTest {
    @Test
    fun `minimum touch target remains accessible`() {
        assertEquals(48f, HarnessMinimumTouchTarget.value)
    }

    @Test
    fun `warning tone is available for recovery surfaces`() {
        assertTrue(HarnessStatusTone.entries.contains(HarnessStatusTone.WARNING))
    }
}
