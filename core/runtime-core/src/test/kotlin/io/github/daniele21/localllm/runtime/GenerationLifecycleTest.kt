package io.github.daniele21.localllm.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationLifecycleTest {
    @Test
    fun `starts open without cancellation or terminal state`() {
        val lifecycle = GenerationLifecycle()

        assertEquals(GenerationLifecycleState.OPEN, lifecycle.state())
        assertFalse(lifecycle.isCancellationRequested())
        assertFalse(lifecycle.isTerminal())
    }

    @Test
    fun `cancellation intent is recorded once and repeated cancellation is idempotent`() {
        val lifecycle = GenerationLifecycle()

        assertTrue(lifecycle.requestCancellation())
        assertFalse(lifecycle.requestCancellation())
        assertEquals(GenerationLifecycleState.CANCELLING, lifecycle.state())
        assertTrue(lifecycle.isCancellationRequested())
        assertFalse(lifecycle.isTerminal())
    }

    @Test
    fun `normal completion reserves terminal delivery exactly once`() {
        val lifecycle = GenerationLifecycle()

        assertTrue(lifecycle.tryFinish())
        assertFalse(lifecycle.tryFinish())
        assertEquals(GenerationLifecycleState.TERMINAL, lifecycle.state())
        assertFalse(lifecycle.requestCancellation())
        assertTrue(lifecycle.isTerminal())
    }

    @Test
    fun `cancelling request can terminate exactly once`() {
        val lifecycle = GenerationLifecycle()

        assertTrue(lifecycle.requestCancellation())
        assertTrue(lifecycle.tryFinish())
        assertFalse(lifecycle.tryFinish())
        assertEquals(GenerationLifecycleState.TERMINAL, lifecycle.state())
        assertFalse(lifecycle.isCancellationRequested())
        assertTrue(lifecycle.isTerminal())
    }
}
