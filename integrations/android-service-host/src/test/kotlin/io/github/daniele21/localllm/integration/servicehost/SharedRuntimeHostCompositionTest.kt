package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRuntimeHostCompositionTest {
    @Test
    fun `default host protocol info retains legacy v1 surface without consumer features`() {
        val info = hostProtocolInfo("phone-test-0.5.0-debug")

        assertEquals(BinderProtocolV1.MAJOR, info.protocolMajor)
        assertEquals(BinderProtocolV1.MINOR, info.protocolMinor)
        assertEquals(BinderProtocolV1.MIN_SUPPORTED_MINOR, info.minSupportedMinor)
        assertFalse(BinderProtocolV1.FEATURE_CONSUMER_API_V1 in info.supportedFeatures)
        assertFalse(BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1 in info.supportedFeatures)
        assertEquals("phone-test-0.5.0-debug", info.hostBuildId)
    }

    @Test
    fun `consumer-enabled host advertises v1 inference without unwired control plane`() {
        val info = hostProtocolInfo("phone-test-0.5.0-debug", consumerApiEnabled = true)

        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_API_V1 in info.supportedFeatures)
        assertFalse(BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1 in info.supportedFeatures)
        assertEquals(
            (BinderProtocolV1.KNOWN_FEATURES - BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1).sorted(),
            info.supportedFeatures,
        )
    }

    @Test
    fun `fully wired consumer host advertises control plane additively`() {
        val info = hostProtocolInfo(
            "phone-test-0.5.0-debug",
            consumerApiEnabled = true,
            consumerControlPlaneEnabled = true,
        )

        assertEquals(BinderProtocolV1.KNOWN_FEATURES.sorted(), info.supportedFeatures)
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_API_V1 in info.supportedFeatures)
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1 in info.supportedFeatures)
    }

    @Test
    fun `control plane cannot be advertised without consumer inference API`() {
        assertThrows(IllegalArgumentException::class.java) {
            hostProtocolInfo(
                "phone-test-0.5.0-debug",
                consumerApiEnabled = false,
                consumerControlPlaneEnabled = true,
            )
        }
    }

    @Test
    fun `host build id is bounded before exposing the binder`() {
        val oversized = "x".repeat(BinderProtocolV1.MAX_CLIENT_BUILD_ID_CHARACTERS + 1)

        assertThrows(IllegalArgumentException::class.java) {
            hostProtocolInfo(oversized)
        }
    }
}
