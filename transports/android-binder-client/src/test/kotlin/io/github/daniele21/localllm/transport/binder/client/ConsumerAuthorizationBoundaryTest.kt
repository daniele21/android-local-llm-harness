package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerAuthorizationBoundaryTest {
    private val host = SharedRuntimeHostConfig.create("io.github.example.host", ".SharedRuntimeService")
    private val hello =
        ClientHelloParcel(
            protocolMajor = BinderProtocolV1.MAJOR,
            protocolMinor = BinderProtocolV1.MINOR,
            minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
            requiredFeatures = setOf(BinderProtocolV1.FEATURE_CONSUMER_API_V1).sorted(),
            clientBuildId = "authorization-boundary-test",
        )

    @Test
    fun `consumer api security exception becomes permission denied without exposing endpoint`() {
        val binding = CapturingBinding()
        val observed = mutableListOf<SharedRuntimeConnectionSnapshot>()
        val connection =
            SharedRuntimeConnection(
                hostConfig = host,
                clientHello = hello,
                binding = binding,
                observer = SharedRuntimeConnectionObserver { observed += it },
            )
        val service = DenyingConsumerAccessService(FakeSharedRuntimeRemoteService())

        connection.connect()
        binding.connect(service)

        assertEquals(SharedRuntimeConnectionState.PERMISSION_DENIED, connection.snapshot.state)
        assertEquals("Caller is not authorized", connection.snapshot.detail)
        assertNull(connection.endpoint)
        assertEquals(1, binding.unbindCalls)
        assertTrue(observed.none { it.state == SharedRuntimeConnectionState.CONNECTED })
    }

    private class CapturingBinding : SharedRuntimeBinding {
        private var callbacks: SharedRuntimeBindingCallbacks? = null
        var unbindCalls = 0
            private set

        override fun hostExists(hostConfig: SharedRuntimeHostConfig): Boolean = true

        override fun bind(
            hostConfig: SharedRuntimeHostConfig,
            callbacks: SharedRuntimeBindingCallbacks,
        ): SharedRuntimeBindResult {
            this.callbacks = callbacks
            return SharedRuntimeBindResult.STARTED
        }

        override fun unbind() {
            unbindCalls += 1
            callbacks = null
        }

        fun connect(service: SharedRuntimeRemoteService) {
            requireNotNull(callbacks).onConnected(service)
        }
    }

    private class DenyingConsumerAccessService(
        delegate: SharedRuntimeRemoteService,
    ) : SharedRuntimeRemoteService by delegate {
        override val consumer: ConsumerSharedRuntimeRemoteService
            get() = throw SecurityException("Caller is not authorized")
    }
}
