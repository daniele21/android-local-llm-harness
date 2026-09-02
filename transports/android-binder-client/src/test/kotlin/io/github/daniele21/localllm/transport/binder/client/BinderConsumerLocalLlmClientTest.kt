package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.contracts.ConsumerSetupResolutionRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class BinderConsumerLocalLlmClientTest {
    @Test
    fun `setup resolution is explicitly forwarded by concrete client`() {
        val method =
            BinderConsumerLocalLlmClient::class.java.getDeclaredMethod(
                "resolveSetup",
                ConsumerSetupResolutionRequest::class.java,
            )

        assertEquals(BinderConsumerLocalLlmClient::class.java, method.declaringClass)
    }
}
