package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SharedRuntimeHostCompositionTest {
    @Test
    fun `default host protocol info advertises the v1 contract deterministically`() {
        val info = hostProtocolInfo("phone-test-0.5.0-debug")

        assertEquals(BinderProtocolV1.MAJOR, info.protocolMajor)
        assertEquals(BinderProtocolV1.MINOR, info.protocolMinor)
        assertEquals(BinderProtocolV1.MIN_SUPPORTED_MINOR, info.minSupportedMinor)
        assertEquals(BinderProtocolV1.KNOWN_FEATURES.sorted(), info.supportedFeatures)
        assertEquals("phone-test-0.5.0-debug", info.hostBuildId)
    }

    @Test
    fun `host build id is bounded before exposing the binder`() {
        val oversized = "x".repeat(BinderProtocolV1.MAX_CLIENT_BUILD_ID_CHARACTERS + 1)

        assertThrows(IllegalArgumentException::class.java) {
            hostProtocolInfo(oversized)
        }
    }
}
