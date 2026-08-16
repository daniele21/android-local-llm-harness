package io.github.daniele21.localllm.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelResidencyLifecycleTest {
    @Test
    fun `successful load and unload follow the residency transition table`() {
        val lifecycle = ModelResidencyLifecycle()

        lifecycle.beginLoad()
        assertEquals(ModelResidencyState.LOADING, lifecycle.state())

        lifecycle.loadSucceeded()
        assertEquals(ModelResidencyState.RESIDENT, lifecycle.state())

        assertTrue(lifecycle.tryBeginUnload())
        assertEquals(ModelResidencyState.UNLOADING, lifecycle.state())

        lifecycle.unloadSucceeded()
        assertEquals(ModelResidencyState.EMPTY, lifecycle.state())
    }

    @Test
    fun `failed load rolls residency back to empty`() {
        val lifecycle = ModelResidencyLifecycle()

        lifecycle.beginLoad()
        lifecycle.loadFailed()

        assertEquals(ModelResidencyState.EMPTY, lifecycle.state())
        lifecycle.beginLoad()
        lifecycle.loadSucceeded()
        assertEquals(ModelResidencyState.RESIDENT, lifecycle.state())
    }

    @Test
    fun `failed unload restores resident state and allows retry`() {
        val lifecycle = residentLifecycle()

        assertTrue(lifecycle.tryBeginUnload())
        lifecycle.unloadFailed()

        assertEquals(ModelResidencyState.RESIDENT, lifecycle.state())
        assertTrue(lifecycle.tryBeginUnload())
        lifecycle.unloadSucceeded()
        assertEquals(ModelResidencyState.EMPTY, lifecycle.state())
    }

    @Test
    fun `repeated unload reservation is idempotent while release is in progress`() {
        val lifecycle = residentLifecycle()

        assertTrue(lifecycle.tryBeginUnload())
        assertFalse(lifecycle.tryBeginUnload())
        assertEquals(ModelResidencyState.UNLOADING, lifecycle.state())
    }

    @Test
    fun `overlapping load and unload transitions fail closed`() {
        val lifecycle = ModelResidencyLifecycle()
        lifecycle.beginLoad()

        assertTrue(runCatching { lifecycle.beginLoad() }.isFailure)
        assertTrue(runCatching { lifecycle.tryBeginUnload() }.isFailure)
        assertEquals(ModelResidencyState.LOADING, lifecycle.state())
    }

    private fun residentLifecycle(): ModelResidencyLifecycle = ModelResidencyLifecycle().apply {
        beginLoad()
        loadSucceeded()
    }
}
