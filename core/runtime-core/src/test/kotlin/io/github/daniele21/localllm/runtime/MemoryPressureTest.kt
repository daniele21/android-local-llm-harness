package io.github.daniele21.localllm.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeMemoryPolicyTest {
    private val policy = RuntimeMemoryPolicy()

    @Test
    fun `hidden idle runtime unloads warm model`() {
        assertEquals(
            RuntimeMemoryAction.UNLOAD_IDLE_MODEL,
            policy.decide(
                RuntimeMemoryPressure.UI_HIDDEN,
                RuntimeMemoryResourceSnapshot(true, 0, false, 0),
            ),
        )
    }

    @Test
    fun `background signal preserves active session`() {
        assertEquals(
            RuntimeMemoryAction.NONE,
            policy.decide(
                RuntimeMemoryPressure.BACKGROUND,
                RuntimeMemoryResourceSnapshot(true, 1, false, 0),
            ),
        )
    }

    @Test
    fun `low memory cancels active work and releases resources`() {
        assertEquals(
            RuntimeMemoryAction.CANCEL_AND_RELEASE_ALL,
            policy.decide(
                RuntimeMemoryPressure.LOW_MEMORY,
                RuntimeMemoryResourceSnapshot(true, 1, true, 2),
            ),
        )
    }

    @Test
    fun `empty runtime requires no action`() {
        assertEquals(
            RuntimeMemoryAction.NONE,
            policy.decide(
                RuntimeMemoryPressure.LOW_MEMORY,
                RuntimeMemoryResourceSnapshot(false, 0, false, 0),
            ),
        )
    }
}
