package io.github.daniele21.localllm.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextSizeSelectorTest {
    @Test
    fun `auto selects the smallest supported size containing required tokens`() {
        assertEquals(1_024, ContextSizeSelector.selectAuto(1_024, 16_384, null))
        assertEquals(2_048, ContextSizeSelector.selectAuto(1_025, 16_384, null))
        assertEquals(4_096, ContextSizeSelector.selectAuto(2_049, 16_384, null))
        assertEquals(8_192, ContextSizeSelector.selectAuto(4_097, 16_384, null))
    }

    @Test
    fun `soft preferred minimum selects lower edge without becoming a hard maximum`() {
        assertEquals(2_048, ContextSizeSelector.selectAuto(900, 32_768, 2_048))
        assertEquals(8_192, ContextSizeSelector.selectAuto(6_000, 32_768, 4_096))
        assertEquals(16_384, ContextSizeSelector.selectAuto(9_000, 32_768, 4_096))
    }

    @Test
    fun `auto and manual reject unsupported capacities`() {
        assertNull(ContextSizeSelector.selectAuto(5_000, 6_000, null))
        assertFalse(ContextSizeSelector.supportsManual(3_000, 2_000, 8_192))
        assertFalse(ContextSizeSelector.supportsManual(2_048, 3_000, 8_192))
        assertTrue(ContextSizeSelector.supportsManual(4_096, 3_000, 8_192))
    }

    @Test
    fun `qwen context candidates cap auto and manual selection`() {
        val candidates = listOf(1_024, 2_048, 4_096, 8_192)
        assertEquals(4_096, ContextSizeSelector.selectAuto(3_000, 262_144, null, candidates))
        assertNull(ContextSizeSelector.selectAuto(9_000, 262_144, null, candidates))
        assertTrue(ContextSizeSelector.supportsManual(8_192, 4_000, 262_144, candidates))
        assertFalse(ContextSizeSelector.supportsManual(16_384, 4_000, 262_144, candidates))
    }
}
