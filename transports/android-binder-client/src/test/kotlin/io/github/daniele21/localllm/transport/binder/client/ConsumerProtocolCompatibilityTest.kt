package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerProtocolCompatibilityTest {
    private val host = SharedRuntimeHostConfig.create("io.github.example.host", ".SharedRuntimeService")
    private val legacyFeatures = BinderProtocolV1.KNOWN_FEATURES - BinderProtocolV1.FEATURE_CONSUMER_API_V1

    @Test
    fun `legacy client remains compatible with protocol minor zero host`() {
        val service =
            FakeSharedRuntimeRemoteService(
                protocol = legacyProtocolInfo(),
                registration = legacyRegistration(),
            )
        val binding = CompatibilityBinding()
        val connection = SharedRuntimeConnection(host, legacyHello(), binding)

        connection.connect()
        binding.connectHost(service)

        assertEquals(SharedRuntimeConnectionState.CONNECTED, connection.snapshot.state)
        assertEquals(0, connection.snapshot.negotiatedMinor)
        assertEquals(legacyFeatures, connection.snapshot.enabledFeatures)
        assertEquals(1, service.registerCalls)
    }

    @Test
    fun `consumer client fails before registration when protocol minor zero host lacks feature`() {
        val service =
            FakeSharedRuntimeRemoteService(
                protocol = legacyProtocolInfo(),
                registration = legacyRegistration(),
            )
        val binding = CompatibilityBinding()
        val connection = SharedRuntimeConnection(host, consumerHello(), binding)

        connection.connect()
        binding.connectHost(service)

        assertEquals(SharedRuntimeConnectionState.INCOMPATIBLE, connection.snapshot.state)
        assertNull(connection.endpoint)
        assertEquals(0, service.registerCalls)
        assertEquals(1, binding.unbindCalls)
        assertTrue(connection.snapshot.detail?.contains("feature", ignoreCase = true) == true)
    }

    @Test
    fun `consumer client negotiates protocol minor one only when feature is advertised`() {
        val service = FakeSharedRuntimeRemoteService()
        val binding = CompatibilityBinding()
        val connection = SharedRuntimeConnection(host, consumerHello(), binding)

        connection.connect()
        binding.connectHost(service)

        assertEquals(SharedRuntimeConnectionState.CONNECTED, connection.snapshot.state)
        assertEquals(BinderProtocolV1.MINOR, connection.snapshot.negotiatedMinor)
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_API_V1 in connection.snapshot.enabledFeatures)
        assertEquals(1, service.registerCalls)
    }

    private fun legacyProtocolInfo() = ProtocolInfoParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = 0,
        minSupportedMinor = 0,
        supportedFeatures = legacyFeatures.sorted(),
        hostBuildId = "legacy-host",
    )

    private fun legacyRegistration() = RegistrationResultParcel(
        clientToken = ClientTokenParcel("legacy-client-token"),
        negotiatedMinor = 0,
        enabledFeatures = legacyFeatures.sorted(),
        error = null,
    )

    private fun legacyHello() = ClientHelloParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        requiredFeatures = emptyList(),
        clientBuildId = "legacy-client",
    )

    private fun consumerHello() = legacyHello().copy(
        requiredFeatures = listOf(BinderProtocolV1.FEATURE_CONSUMER_API_V1),
        clientBuildId = "consumer-client",
    )

    private class CompatibilityBinding : SharedRuntimeBinding {
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
            unbindCalls += 1
            callbacks = null
        }

        fun connectHost(service: SharedRuntimeRemoteService) {
            requireNotNull(callbacks).onConnected(service)
        }
    }
}
