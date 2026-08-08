package io.github.daniele21.localllm.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qwen35RuntimeTuningTest {
    @Test
    fun `candidate profiles are conservative and backend pinned`() {
        Qwen35ModelTier.entries.forEach { tier ->
            val profile = Qwen35RuntimeTuningProfiles.candidateForTier(tier)
            val capabilities = profile.runtimeCapabilities()

            assertEquals(Qwen35RuntimeEvidenceStatus.CANDIDATE, profile.evidenceStatus)
            assertEquals("llama.cpp", capabilities.requiredBackendId)
            assertEquals(Qwen35RuntimeTuningProfiles.LLAMA_CPP_REVISION, capabilities.requiredBackendRevision)
            assertEquals(listOf(1_024, 2_048, 4_096, 8_192), capabilities.approvedContextTiers)
            assertFalse(capabilities.supportsStatelessContextReuse)
            assertFalse(capabilities.supportsPrefixSnapshot)
            assertFalse(capabilities.supportsSessionRestore)
            assertFalse(capabilities.supportsPrefixReuse)
        }
    }

    @Test
    fun `tuning matrix is stable and covers controlled candidates`() {
        val matrix = Qwen35RuntimeTuningProfiles.tuningMatrixForTier(Qwen35ModelTier.B0_8)

        assertEquals(12, matrix.size)
        assertEquals(12, matrix.map { it.stableId }.distinct().size)
        assertEquals(setOf(1_024, 2_048, 4_096), matrix.map { it.contextTokens }.toSet())
        assertEquals(setOf(2, 4), matrix.map { it.cpuThreads }.toSet())
        assertTrue(matrix.any { it.batchSize == 64 && it.microBatchSize == 32 })
        assertTrue(matrix.any { it.batchSize == 128 && it.microBatchSize == 64 })
    }
}
