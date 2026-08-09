package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ModelActivationRuntimeContractTest {
    @Test
    fun `playground activation prepares the manual inference use case`() {
        assertEquals(
            "manual-inference-playground",
            HarnessRuntimePurpose.PLAYGROUND.useCaseId.value,
        )
    }

    @Test
    fun `playground activation remains distinct from physical validation`() {
        assertNotEquals(
            HarnessRuntimePurpose.PHYSICAL_VALIDATION.useCaseId,
            HarnessRuntimePurpose.PLAYGROUND.useCaseId,
        )
    }
}
