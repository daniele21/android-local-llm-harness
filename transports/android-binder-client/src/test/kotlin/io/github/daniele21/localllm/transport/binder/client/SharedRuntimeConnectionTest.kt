package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRuntimeConnectionTest {
    private val host = SharedRuntimeHostConfig.create("io.github.example.host", ".SharedRuntimeService")
    private val hello = ClientHelloParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        requiredFeatures = emptyList(),
        clientBuildId = "test-client",
    )

    @Test
    fun `missing host fails without bind`() {
        val binding = FakeBinding(hostExists = false)
        val connection = SharedRuntimeConnection(host, hello, binding)

        connection.connect()

        assertEquals(SharedRuntimeConnectionState.HOST_NOT_INSTALLED, connection.snapshot.state)
        assertEquals(0, binding.bindCalls)
    }

    @Test
    fun `permission denial is typed and does not connect`() {
        val binding = FakeBinding(bindResult = SharedRuntimeBindResult.PERMISSION_DENIED)
        val connection = SharedRuntimeConnection(host, hello, binding)

        connection.connect()

        assertEquals(SharedRuntimeConnectionState.PERMISSION_DENIED, connection.snapshot.state)
    }

    @Test
    fun `compatible host negotiates and connects`() {
        val binding = FakeBinding()
        val connection = SharedRuntimeConnection(host, hello, binding)

        connection.connect()
        binding.connectHost(protocolInfo())

        assertEquals(SharedRuntimeConnectionState.CONNECTED, connection.snapshot.state)
        assertEquals(BinderProtocolV1.MINOR, connection.snapshot.negotiatedMinor)
    }

    @Test
    fun `major mismatch becomes incompatible and unbinds`() {
        val binding = FakeBinding()
        val connection = SharedRuntimeConnection(host, hello, binding)

        connection.connect()
        binding.connectHost(protocolInfo(protocolMajor = BinderProtocolV1.MAJOR + 1))

        assertEquals(SharedRuntimeConnectionState.INCOMPATIBLE, connection.snapshot.state)
        assertEquals(1, binding.unbindCalls)
    }

    @Test
    fun `disconnect after connect becomes connection lost and requires explicit reconnect`() {
        val binding = FakeBinding()
        val connection = SharedRuntimeConnection(host, hello, binding)

        connection.connect()
        binding.connectHost(protocolInfo())
        binding.disconnectHost()

        assertEquals(SharedRuntimeConnectionState.CONNECTION_LOST, connection.snapshot.state)
        assertEquals(1, binding.unbindCalls)

        connection.connect()
        assertEquals(2, binding.bindCalls)
    }

    @Test
    fun `connect and close are idempotent`() {
        val binding = FakeBinding()
        val observed = mutableListOf<SharedRuntimeConnectionState>()
        val connection = SharedRuntimeConnection(
            host,
            hello,
            binding,
            SharedRuntimeConnectionObserver { observed += it.state },
        )

        connection.connect()
        connection.connect()
        connection.close()
        connection.close()
        connection.connect()

        assertEquals(1, binding.bindCalls)
        assertEquals(1, binding.unbindCalls)
        assertEquals(SharedRuntimeConnectionState.CLOSED, connection.snapshot.state)
        assertTrue(observed.contains(SharedRuntimeConnectionState.BINDING))
        assertEquals(SharedRuntimeConnectionState.CLOSED, observed.last())
    }

    private fun protocolInfo(protocolMajor: Int = BinderProtocolV1.MAJOR): ProtocolInfoParcel = ProtocolInfoParcel(
        protocolMajor = protocolMajor,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        supportedFeatures = BinderProtocolV1.KNOWN_FEATURES.sorted(),
        hostBuildId = "test-host",
    )

    private class FakeBinding(
        private val hostExists: Boolean = true,
        private val bindResult: SharedRuntimeBindResult = SharedRuntimeBindResult.STARTED,
    ) : SharedRuntimeBinding {
        var bindCalls = 0
        var unbindCalls = 0
        private var callbacks: SharedRuntimeBindingCallbacks? = null

        override fun hostExists(hostConfig: SharedRuntimeHostConfig): Boolean = hostExists

        override fun bind(hostConfig: SharedRuntimeHostConfig, callbacks: SharedRuntimeBindingCallbacks): SharedRuntimeBindResult {
            bindCalls += 1
            if (bindResult == SharedRuntimeBindResult.STARTED) {
                this.callbacks = callbacks
            }
            return bindResult
        }

        override fun unbind() {
            unbindCalls += 1
            callbacks = null
        }

        fun connectHost(info: ProtocolInfoParcel) {
            callbacks?.onConnected(SharedRuntimeProtocolService { info })
        }

        fun disconnectHost() {
            callbacks?.onDisconnected()
        }
    }
}
