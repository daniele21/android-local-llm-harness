package io.github.daniele21.localllm.transport.binder.client

import org.junit.Assert.assertEquals
import org.junit.Test

class BinderConsumerDisconnectSurfaceTest {
    @Test
    fun `consumer client exposes reusable disconnect`() {
        val method = BinderConsumerLocalLlmClient::class.java.getDeclaredMethod("disconnect")

        assertEquals(Void.TYPE, method.returnType)
        assertEquals(0, method.parameterCount)
    }
}
