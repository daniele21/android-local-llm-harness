package io.github.daniele21.localllm.llamacpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KvCacheTypePolicyTest {
    @Test
    fun `accepts exact cache type names exposed by pinned llama cpp`() {
        val expected = mapOf(
            "f32" to NativeKvCacheType.F32,
            "f16" to NativeKvCacheType.F16,
            "bf16" to NativeKvCacheType.BF16,
            "q8_0" to NativeKvCacheType.Q8_0,
            "q4_0" to NativeKvCacheType.Q4_0,
            "q4_1" to NativeKvCacheType.Q4_1,
            "iq4_nl" to NativeKvCacheType.IQ4_NL,
            "q5_0" to NativeKvCacheType.Q5_0,
            "q5_1" to NativeKvCacheType.Q5_1,
        )

        expected.forEach { (wireName, type) ->
            assertEquals(type, NativeKvCacheType.fromWireName(wireName))
        }
    }

    @Test
    fun `rejects aliases and unknown cache type names`() {
        assertNull(NativeKvCacheType.fromWireName("F16"))
        assertNull(NativeKvCacheType.fromWireName("q4_k"))
        assertNull(NativeKvCacheType.fromWireName(""))
    }
}
