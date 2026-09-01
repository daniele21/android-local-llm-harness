package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerActivation
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.ConsumerGenerationConfiguration
import io.github.daniele21.localllm.contracts.ConsumerResolvedSetup
import io.github.daniele21.localllm.contracts.ConsumerSetupResolutionRequest
import io.github.daniele21.localllm.contracts.ConsumerSetupResolutionResult
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.ThinkingMode
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
        val negotiated =
            negotiateProtocol(
                host(),
                client(
                    protocolMinor = 2,
                    requiredFeatures =
                        listOf(
                            BinderProtocolV1.FEATURE_CONSUMER_API_V1,
                            BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1,
                        ),
                ),
            )

        assertEquals(2, negotiated.minor)
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1 in negotiated.enabledFeatures)
        assertFalse(BinderProtocolV1.FEATURE_CONSUMER_SETUP_RESOLUTION_V1 in negotiated.enabledFeatures)
    }

    @Test
    fun `version 1_4 client does not negotiate minor-5 setup resolution`() {
        val negotiated = negotiateProtocol(host(), client(protocolMinor = 4))

        assertEquals(4, negotiated.minor)
        assertFalse(BinderProtocolV1.FEATURE_CONSUMER_SETUP_RESOLUTION_V1 in negotiated.enabledFeatures)
    }

    @Test
    fun `version 1_5 client can require read-only setup resolution`() {
        val negotiated =
            negotiateProtocol(
                host(),
                client(
                    protocolMinor = 5,
                    requiredFeatures =
                        listOf(
                            BinderProtocolV1.FEATURE_CONSUMER_API_V1,
                            BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1,
                            BinderProtocolV1.FEATURE_CONSUMER_SETUP_RESOLUTION_V1,
                        ),
                ),
            )

        assertEquals(5, negotiated.minor)
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_SETUP_RESOLUTION_V1 in negotiated.enabledFeatures)
    }

    @Test
    fun `version 1_4 client cannot claim minor-5 setup resolution`() {
        val failure = assertThrows(WireProtocolException::class.java) {
            negotiateProtocol(
                host(),
                client(
                    protocolMinor = 4,
                    requiredFeatures = listOf(BinderProtocolV1.FEATURE_CONSUMER_SETUP_RESOLUTION_V1),
                ),
            )
        }

        assertEquals(WireErrorCodes.FEATURE_UNAVAILABLE, failure.wireCode)
    }

    @Test
    fun `activation request wire identity contains revisions but no model identity`() {
        val request =
            ConsumerActivationRequest(
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
    fun `setup resolution round trip exposes public profile and effective configuration without private artifact identity`() {
        val request =
            ConsumerSetupResolutionRequest(
                useCaseId = UseCaseId("document-pii-detection"),
                useCaseRevision = 4,
                bindingRevision = 8,
                preset = InferencePresetRef(InferencePresetId("balanced"), 3),
            )
        val wireRequest =
            request.toConsumerControlPlaneWire(ClientTokenParcel("opaque-token"), "operation-setup")
        assertFalse(wireRequest.toString().contains("digest", ignoreCase = true))

        val result =
            ConsumerSetupResolutionResult.Resolved(
                ConsumerResolvedSetup(
                    useCaseId = request.useCaseId,
                    useCaseRevision = request.useCaseRevision,
                    bindingRevision = request.bindingRevision,
                    preset = request.preset,
                    modelProfileId = "qwen35-0.8b-ombra-pii",
                    contextTokens = 4096,
                    generation =
                        ConsumerGenerationConfiguration(
                            maxOutputTokens = 384,
                            temperature = 0.1f,
                            topP = 0.9f,
                            topK = 20,
                            minP = 0.05f,
                            presencePenalty = 0f,
                            repeatPenalty = 1.1f,
                            repeatLastN = 64,
                            thinkingMode = ThinkingMode.DISABLED,
                            seedPolicy = SeedPolicyType.RANDOM,
                        ),
                ),
            )
        val wire = result.toConsumerControlPlaneWire("operation-setup")
        val roundTrip = wire.toCoreSetupResolutionResult() as ConsumerSetupResolutionResult.Resolved

        assertEquals("qwen35-0.8b-ombra-pii", roundTrip.setup.modelProfileId)
        assertEquals(384, roundTrip.setup.generation.maxOutputTokens)
        assertEquals(4096, roundTrip.setup.contextTokens)
        assertFalse(wire.toString().contains("digest", ignoreCase = true))
        assertFalse(wire.toString().contains("path", ignoreCase = true))
    }

    @Test
    fun `activation response preserves opaque activation identity`() {
        val result =
            ConsumerActivationResult.Activated(
                ConsumerActivation(
                    activationId = ConsumerActivationId("activation-opaque"),
                    useCaseId = UseCaseId("document-pii-detection"),
                    useCaseRevision = 4,
                    bindingRevision = 8,
                    preset = InferencePresetRef(InferencePresetId("balanced"), 3),
                ),
            )

        val roundTrip =
            result.toConsumerControlPlaneWire("operation-2").toCoreActivationResult()
                as ConsumerActivationResult.Activated

        assertEquals(ConsumerActivationId("activation-opaque"), roundTrip.activation.activationId)
        assertEquals(8, roundTrip.activation.bindingRevision)
    }

    private fun host() = ProtocolInfoParcel(
        protocolMajor = 1,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = 0,
        supportedFeatures =
            listOf(
                BinderProtocolV1.FEATURE_CONSUMER_API_V1,
                BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1,
                BinderProtocolV1.FEATURE_CONSUMER_RUNTIME_READINESS_V1,
                BinderProtocolV1.FEATURE_CONSUMER_SETUP_RESOLUTION_V1,
            ),
        hostBuildId = "host-1.${BinderProtocolV1.MINOR}",
    )

    private fun client(protocolMinor: Int, requiredFeatures: List<String> = listOf(BinderProtocolV1.FEATURE_CONSUMER_API_V1)) =
        ClientHelloParcel(
            protocolMajor = 1,
            protocolMinor = protocolMinor,
            minSupportedMinor = 0,
            requiredFeatures = requiredFeatures,
            clientBuildId = "client-$protocolMinor",
        )
}
