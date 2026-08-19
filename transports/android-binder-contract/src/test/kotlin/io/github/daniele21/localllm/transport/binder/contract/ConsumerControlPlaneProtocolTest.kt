package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerControlPlaneProtocolTest {
    @Test
    fun `version 1_1 client negotiates without version 1_2 control-plane feature`() {
        val negotiated = negotiateProtocol(host(), client(protocolMinor = 1))

        assertEquals(1, negotiated.minor)
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_API_V1 in negotiated.enabledFeatures)
        assertFalse(BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1 in negotiated.enabledFeatures)
    }

    @Test
    fun `version 1_2 client can require consumer control plane`() {
        val negotiated = negotiateProtocol(
            host(),
            client(
                protocolMinor = 2,
                requiredFeatures = listOf(
                    BinderProtocolV1.FEATURE_CONSUMER_API_V1,
                    BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1,
                ),
            ),
        )

        assertEquals(2, negotiated.minor)
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1 in negotiated.enabledFeatures)
    }

    @Test
    fun `version 1_1 client cannot claim version 1_2 control-plane feature`() {
        val failure = assertThrows(WireProtocolException::class.java) {
            negotiateProtocol(
                host(),
                client(
                    protocolMinor = 1,
                    requiredFeatures = listOf(BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1),
                ),
            )
        }

        assertEquals(WireErrorCodes.FEATURE_UNAVAILABLE, failure.wireCode)
    }

    @Test
    fun `activation request wire identity contains revisions but no model identity`() {
        val request = ConsumerActivationRequest(
            useCaseId = UseCaseId("document-pii-detection"),
            useCaseRevision = 4,
            bindingRevision = 8,
            preset = InferencePresetRef(InferencePresetId("balanced"), 3),
        )

        val wire = request.toConsumerControlPlaneWire(ClientTokenParcel("opaque-token"), "operation-1")

        assertEquals("document-pii-detection", wire.useCaseId)
        assertEquals(4, wire.useCaseRevision)
        assertEquals(8, wire.bindingRevision)
        assertEquals("balanced", wire.preset?.id)
        assertEquals(3, wire.preset?.version)
        assertFalse(wire.toString().contains("digest", ignoreCase = true))
        assertFalse(wire.toString().contains("model", ignoreCase = true))
    }

    @Test
    fun `activation response preserves opaque activation identity`() {
        val result = ConsumerActivationResult.Activated(
            io.github.daniele21.localllm.contracts.ConsumerActivation(
                activationId = ConsumerActivationId("activation-opaque"),
                useCaseId = UseCaseId("document-pii-detection"),
                useCaseRevision = 4,
                bindingRevision = 8,
                preset = InferencePresetRef(InferencePresetId("balanced"), 3),
            ),
        )

        val roundTrip = result.toConsumerControlPlaneWire("operation-2").toCoreActivationResult()
            as ConsumerActivationResult.Activated

        assertEquals(ConsumerActivationId("activation-opaque"), roundTrip.activation.activationId)
        assertEquals(8, roundTrip.activation.bindingRevision)
    }

    private fun host() = ProtocolInfoParcel(
        protocolMajor = 1,
        protocolMinor = 2,
        minSupportedMinor = 0,
        supportedFeatures = listOf(
            BinderProtocolV1.FEATURE_CONSUMER_API_V1,
            BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1,
        ),
        hostBuildId = "host-1.2",
    )

    private fun client(
        protocolMinor: Int,
        requiredFeatures: List<String> = listOf(BinderProtocolV1.FEATURE_CONSUMER_API_V1),
    ) = ClientHelloParcel(
        protocolMajor = 1,
        protocolMinor = protocolMinor,
        minSupportedMinor = 0,
        requiredFeatures = requiredFeatures,
        clientBuildId = "client-$protocolMinor",
    )
}
