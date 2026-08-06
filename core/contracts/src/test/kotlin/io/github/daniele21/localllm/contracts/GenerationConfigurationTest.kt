package io.github.daniele21.localllm.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GenerationConfigurationTest {
    @Test
    fun `fixed seed accepts the complete native unsigned range`() {
        assertEquals(0L, SeedPolicy.Fixed(0).value)
        assertEquals(MAX_NATIVE_SEED, SeedPolicy.Fixed(MAX_NATIVE_SEED).value)
    }

    @Test
    fun `fixed seed rejects values outside the native range`() {
        assertThrows(IllegalArgumentException::class.java) { SeedPolicy.Fixed(-1) }
        assertThrows(IllegalArgumentException::class.java) { SeedPolicy.Fixed(MAX_NATIVE_SEED + 1) }
    }

    @Test
    fun `generation overrides do not allow ambiguous seed policies`() {
        assertThrows(IllegalArgumentException::class.java) {
            GenerationOverrides(seedPolicy = SeedPolicy.Random, seed = 42)
        }
    }

    @Test
    fun `structured inputs and schemas enforce bounded non-empty content`() {
        assertThrows(IllegalArgumentException::class.java) { GenerationInput.Text(" ") }
        assertThrows(IllegalArgumentException::class.java) { GenerationInput.Messages(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { OutputConstraint.JsonSchema(" ") }
    }
}
