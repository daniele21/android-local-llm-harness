package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRuntimeExplicitDisconnectTest {
    private val host = SharedRuntimeHostConfig.create("io.github.example.host", ".SharedRuntimeService")
    private val hello = ClientHelloParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        requiredFeatures = emptyList(),
        clientBuildId = "disconnect-test",
    )

    @Test
    fun `explicit disconnect releases registration and allows fresh reconnect`() {
        val binding = ReusableBinding()
        val firstService = FakeSharedRuntimeRemoteService()
        val secondService = FakeSharedRuntimeRemoteService()
        val observed = mutableListOf<SharedRuntimeConnectionState>()
        val connection = SharedRuntimeConnection(
            host,
            hello,
            binding,
            SharedRuntimeConnectionObserver { observed += it.state },
        )

        connection.connect()
        binding.connectHost(firstService)
        val firstEpoch = requireNotNull(connection.endpoint).connectionEpoch

        connection.disconnect()

        assertEquals(SharedRuntimeConnectionState.DISCONNECTED, connection.snapshot.state)
        assertNull(connection.endpoint)
        assertEquals(1, firstService.unregisterCalls)
        assertEquals(1, binding.unbindCalls)
        assertTrue(observed.contains(SharedRuntimeConnectionState.DISCONNECTED))

        connection.connect()
        binding.connectHost(secondService)

        assertEquals(SharedRuntimeConnectionState.CONNECTED, connection.snapshot.state)
        assertTrue(requireNotNull(connection.endpoint).connectionEpoch > firstEpoch)
        assertEquals(2, binding.bindCalls)
    }

    @Test
    fun `explicit disconnect is idempotent while already disconnected`() {
        val binding = ReusableBinding()
        val connection = SharedRuntimeConnection(host, hello, binding)

        connection.disconnect()
        connection.disconnect()

        assertEquals(SharedRuntimeConnectionState.DISCONNECTED, connection.snapshot.state)
        assertEquals(0, binding.unbindCalls)
    }

    private class ReusableBinding : SharedRuntimeBinding {
        var bindCalls = 0
        var unbindCalls = 0
        private var callbacks: SharedRuntimeBindingCallbacks? = null

        override fun hostExists(hostConfig: SharedRuntimeHostConfig): Boolean = true

        override fun bind(hostConfig: SharedRuntimeHostConfig, callbacks: SharedRuntimeBindingCallbacks): SharedRuntimeBindResult {
            bindCalls += 1
            this.callbacks = callbacks
            return SharedRuntimeBindResult.STARTED
        }

        override fun unbind() {
            if (callbacks != null) {
                unbindCalls += 1
                callbacks = null
            }
        }

        fun connectHost(service: SharedRuntimeRemoteService) {
            requireNotNull(callbacks).onConnected(service)
        }
    }
}
