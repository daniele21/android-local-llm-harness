package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireErrorParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `compatible host registers before connected`() {
        val binding = FakeBinding()
        val service = FakeSharedRuntimeRemoteService()
        val connection = SharedRuntimeConnection(host, hello, binding)

        connection.connect()
        binding.connectHost(service)

        assertEquals(1, service.registerCalls)
        assertEquals(SharedRuntimeConnectionState.CONNECTED, connection.snapshot.state)
        assertEquals(BinderProtocolV1.MINOR, connection.snapshot.negotiatedMinor)
        assertEquals("test-client-token", connection.endpoint?.clientToken?.value)
    }

    @Test
    fun `registration failure never exposes a usable endpoint`() {
        val service = FakeSharedRuntimeRemoteService(
            registration = successfulRegistration().copy(
                clientToken = null,
                negotiatedMinor = null,
                enabledFeatures = emptyList(),
                error = WireErrorParcel(
                    code = WireErrorCodes.CLIENT_NOT_REGISTERED,
                    safeMessage = "Client is not registered",
                    retryable = false,
                ),
            ),
        )
        val binding = FakeBinding()
        val connection = SharedRuntimeConnection(host, hello, binding)

        connection.connect()
        binding.connectHost(service)

        assertEquals(SharedRuntimeConnectionState.PERMISSION_DENIED, connection.snapshot.state)
        assertNull(connection.endpoint)
    }

    @Test
    fun `major mismatch becomes incompatible and unbinds without registration`() {
        val binding = FakeBinding()
        val service = FakeSharedRuntimeRemoteService(protocol = compatibleProtocolInfo(BinderProtocolV1.MAJOR + 1))
        val connection = SharedRuntimeConnection(host, hello, binding)

        connection.connect()
        binding.connectHost(service)

        assertEquals(SharedRuntimeConnectionState.INCOMPATIBLE, connection.snapshot.state)
        assertEquals(0, service.registerCalls)
        assertEquals(1, binding.unbindCalls)
    }

    @Test
    fun `disconnect after registration becomes connection lost and requires explicit reconnect`() {
        val binding = FakeBinding()
        val service = FakeSharedRuntimeRemoteService()
        val connection = SharedRuntimeConnection(host, hello, binding)

        connection.connect()
        binding.connectHost(service)
        binding.disconnectHost()

        assertEquals(SharedRuntimeConnectionState.CONNECTION_LOST, connection.snapshot.state)
        assertNull(connection.endpoint)
        assertEquals(1, binding.unbindCalls)

        connection.connect()
        assertEquals(2, binding.bindCalls)
    }

    @Test
    fun `close unregisters an established client exactly once`() {
        val binding = FakeBinding()
        val service = FakeSharedRuntimeRemoteService()
        val connection = SharedRuntimeConnection(host, hello, binding)

        connection.connect()
        binding.connectHost(service)
        connection.close()
        connection.close()

        assertEquals(1, service.unregisterCalls)
        assertEquals(1, binding.unbindCalls)
        assertEquals(SharedRuntimeConnectionState.CLOSED, connection.snapshot.state)
    }

    @Test
    fun `connect and close are idempotent while binding`() {
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

        fun connectHost(service: SharedRuntimeRemoteService) {
            callbacks?.onConnected(service)
        }

        fun disconnectHost() {
            callbacks?.onDisconnected()
        }
    }
}
