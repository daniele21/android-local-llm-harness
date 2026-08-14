package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRuntimeHostCompositionTest {
    @Test
    fun `default host protocol info retains legacy v1 surface without consumer feature`() {
        val info = hostProtocolInfo("phone-test-0.5.0-debug")

        assertEquals(BinderProtocolV1.MAJOR, info.protocolMajor)
        assertEquals(BinderProtocolV1.MINOR, info.protocolMinor)
        assertEquals(BinderProtocolV1.MIN_SUPPORTED_MINOR, info.minSupportedMinor)
        assertEquals(
            (BinderProtocolV1.KNOWN_FEATURES - BinderProtocolV1.FEATURE_CONSUMER_API_V1).sorted(),
            info.supportedFeatures,
        )
        assertFalse(BinderProtocolV1.FEATURE_CONSUMER_API_V1 in info.supportedFeatures)
        assertEquals("phone-test-0.5.0-debug", info.hostBuildId)
    }

    @Test
    fun `consumer-enabled host advertises consumer API v1 additively`() {
        val info = hostProtocolInfo("phone-test-0.5.0-debug", consumerApiEnabled = true)

        assertEquals(BinderProtocolV1.KNOWN_FEATURES.sorted(), info.supportedFeatures)
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_API_V1 in info.supportedFeatures)
    }

    @Test
    fun `host build id is bounded before exposing the binder`() {
        val oversized = "x".repeat(BinderProtocolV1.MAX_CLIENT_BUILD_ID_CHARACTERS + 1)

        assertThrows(IllegalArgumentException::class.java) {
            hostProtocolInfo(oversized)
        }
    }
}
