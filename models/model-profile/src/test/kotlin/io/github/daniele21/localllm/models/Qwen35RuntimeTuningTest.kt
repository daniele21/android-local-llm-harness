package io.github.daniele21.localllm.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Qwen35RuntimeTuningTest {
    @Test
    fun `candidate profiles are conservative and backend pinned`() {
        val profiles = Qwen35ModelTier.entries.map(Qwen35RuntimeTuningProfiles::candidateForTier)

        assertEquals(profiles.size, profiles.map { it.id }.distinct().size)
        assertNotEquals(profiles.first().id, profiles.last().id)
        profiles.forEach { profile ->
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
    fun `tuning matrix is complete and independent for every qwen35 tier`() {
        Qwen35ModelTier.entries.forEach { tier ->
            val matrix = Qwen35RuntimeTuningProfiles.tuningMatrixForTier(tier)

            assertEquals(16, matrix.size)
            assertEquals(16, matrix.map { it.stableId }.distinct().size)
            assertEquals(setOf(tier), matrix.map { it.tier }.toSet())
            assertEquals(setOf(1_024, 2_048, 4_096, 8_192), matrix.map { it.contextTokens }.toSet())
            assertEquals(setOf(2, 4), matrix.map { it.cpuThreads }.toSet())
            assertTrue(matrix.all { it.batchThreads == it.cpuThreads })
            assertTrue(matrix.any { it.batchSize == 64 && it.microBatchSize == 32 })
            assertTrue(matrix.any { it.batchSize == 128 && it.microBatchSize == 64 })
        }
    }
}
