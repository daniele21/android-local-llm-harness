package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import org.junit.Assert.assertEquals
import org.junit.Test

class HarnessApplicationsReadPresentationTest {
    @Test
    fun `application statuses map to non-color-only labels and semantic tones`() {
        assertEquals("Enabled", HarnessApplicationStatus.AUTHORIZED.label())
        assertEquals(HarnessStatusTone.SUCCESS, HarnessApplicationStatus.AUTHORIZED.tone())
        assertEquals("Pending", HarnessApplicationStatus.PENDING.label())
        assertEquals(HarnessStatusTone.INFO, HarnessApplicationStatus.PENDING.tone())
        assertEquals("Disabled", HarnessApplicationStatus.DISABLED.label())
        assertEquals(HarnessStatusTone.NEUTRAL, HarnessApplicationStatus.DISABLED.tone())
        assertEquals("Identity changed", HarnessApplicationStatus.IDENTITY_CHANGED.label())
        assertEquals(HarnessStatusTone.WARNING, HarnessApplicationStatus.IDENTITY_CHANGED.tone())
        assertEquals("Unavailable", HarnessApplicationStatus.UNAVAILABLE.label())
        assertEquals(HarnessStatusTone.ERROR, HarnessApplicationStatus.UNAVAILABLE.tone())
    }

    @Test
    fun `assignment count copy handles singular and plural`() {
        assertEquals("0 assigned use cases", assignmentCountLabel(0))
        assertEquals("1 assigned use case", assignmentCountLabel(1))
        assertEquals("2 assigned use cases", assignmentCountLabel(2))
    }
}
