package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerPreparationAction
import io.github.daniele21.localllm.contracts.ConsumerRuntimeIssue
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadiness
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadinessResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerRuntimeReadinessProtocolTest {
    @Test
    fun `version 1_3 client remains compatible without readiness feature`() {
        val negotiated = negotiateProtocol(host(), client(protocolMinor = 3))

        assertEquals(3, negotiated.minor)
        assertFalse(BinderProtocolV1.FEATURE_CONSUMER_RUNTIME_READINESS_V1 in negotiated.enabledFeatures)
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1 in negotiated.enabledFeatures)
    }

    @Test
    fun `version 1_4 client can require readiness feature`() {
        val negotiated = negotiateProtocol(
            host(),
            client(
                protocolMinor = 4,
                requiredFeatures = listOf(BinderProtocolV1.FEATURE_CONSUMER_RUNTIME_READINESS_V1),
            ),
        )

        assertEquals(4, negotiated.minor)
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_RUNTIME_READINESS_V1 in negotiated.enabledFeatures)
    }

    @Test
    fun `version 1_3 client cannot claim readiness feature`() {
        val failure = assertThrows(WireProtocolException::class.java) {
            negotiateProtocol(
                host(),
                client(
                    protocolMinor = 3,
                    requiredFeatures = listOf(BinderProtocolV1.FEATURE_CONSUMER_RUNTIME_READINESS_V1),
                ),
            )
        }

        assertEquals(WireErrorCodes.FEATURE_UNAVAILABLE, failure.wireCode)
    }

    @Test
    fun `readiness wire round trip preserves lifecycle without host model identity`() {
        val original = ConsumerRuntimeReadinessResult.Available(
            ConsumerRuntimeReadiness(
                activationId = ConsumerActivationId("activation-1"),
                phase = ConsumerRuntimePhase.PREPARING,
                preparationAction = ConsumerPreparationAction.SWITCHING,
            ),
        )

        val wire = original.toConsumerRuntimeReadinessWire("operation-1")
        val roundTrip = wire.toCoreRuntimeReadinessResult() as ConsumerRuntimeReadinessResult.Available

        assertEquals(original, roundTrip)
        assertFalse(wire.toString().contains("digest", ignoreCase = true))
        assertFalse(wire.toString().contains("path", ignoreCase = true))
        assertFalse(wire.toString().contains("model", ignoreCase = true))
    }

    @Test
    fun `invalid phase and issue combination fails closed during wire reconstruction`() {
        val invalid = ConsumerRuntimeReadinessResultParcel(
            operationId = "operation-2",
            activationId = "activation-2",
            phaseTag = ConsumerRuntimePhase.READY.name,
            preparationActionTag = ConsumerPreparationAction.NONE.name,
            issueTag = ConsumerRuntimeIssue.RUNTIME_FAILED.name,
        )

        assertThrows(IllegalArgumentException::class.java) {
            invalid.toCoreRuntimeReadinessResult()
        }
    }

    private fun host() = ProtocolInfoParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        supportedFeatures =
        listOf(
            BinderProtocolV1.FEATURE_CONSUMER_API_V1,
            BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1,
            BinderProtocolV1.FEATURE_CONSUMER_RUNTIME_READINESS_V1,
        ),
        hostBuildId = "host-1.4",
    )

    private fun client(protocolMinor: Int, requiredFeatures: List<String> = emptyList()) = ClientHelloParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = protocolMinor,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        requiredFeatures = requiredFeatures,
        clientBuildId = "client-$protocolMinor",
    )
}
