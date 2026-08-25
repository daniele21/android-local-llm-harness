package io.github.daniele21.localllm.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Qwen35RuntimeAcceptanceTest {
    @Test
    fun `keep candidate never promotes a profile`() {
        val candidate = Qwen35RuntimeTuningProfiles.candidateForTier(Qwen35ModelTier.B0_8)
        val evidence = acceptanceFor(
            candidate = candidate,
            decision = Qwen35RuntimeAcceptanceDecision.KEEP_CANDIDATE,
            benchmarkEvidenceSha256 = emptySet(),
            lifecycleEvidenceSha256 = emptySet(),
            reviewedDeviceClasses = emptySet(),
            lifecycleValidated = false,
            memoryValidated = false,
            representativeCoverageValidated = false,
        )

        val reviewed = Qwen35RuntimeProfileAcceptance.reviewedProfileForTier(candidate.tier, evidence)

        assertEquals(Qwen35RuntimeEvidenceStatus.CANDIDATE, reviewed.evidenceStatus)
        assertEquals(candidate, reviewed)
    }

    @Test
    fun `measured promotion requires every acceptance gate`() {
        val candidate = Qwen35RuntimeTuningProfiles.candidateForTier(Qwen35ModelTier.B2)
        val incomplete = acceptanceFor(
            candidate = candidate,
            decision = Qwen35RuntimeAcceptanceDecision.PROMOTE_MEASURED,
            benchmarkEvidenceSha256 = setOf(SHA_A),
            lifecycleEvidenceSha256 = setOf(SHA_B),
            reviewedDeviceClasses = setOf("representative-arm64"),
            lifecycleValidated = true,
            memoryValidated = false,
            representativeCoverageValidated = true,
        )

        assertThrows(IllegalArgumentException::class.java) {
            Qwen35RuntimeProfileAcceptance.reviewedProfileForTier(candidate.tier, incomplete)
        }
    }

    @Test
    fun `measured promotion is explicit and provenance bound`() {
        val candidate = Qwen35RuntimeTuningProfiles.candidateForTier(Qwen35ModelTier.B2)
        val evidence = acceptanceFor(
            candidate = candidate,
            decision = Qwen35RuntimeAcceptanceDecision.PROMOTE_MEASURED,
            benchmarkEvidenceSha256 = setOf(SHA_A),
            lifecycleEvidenceSha256 = setOf(SHA_B),
            reviewedDeviceClasses = setOf("representative-arm64-a", "representative-arm64-b"),
            lifecycleValidated = true,
            memoryValidated = true,
            representativeCoverageValidated = true,
        )

        val measured = Qwen35RuntimeProfileAcceptance.reviewedProfileForTier(candidate.tier, evidence)

        assertEquals(Qwen35RuntimeEvidenceStatus.MEASURED, measured.evidenceStatus)
        assertEquals(candidate.id, measured.id)
        assertEquals(candidate.version, measured.version)
        assertEquals(candidate.tier, measured.tier)
    }

    @Test
    fun `promotion rejects evidence for another profile identity`() {
        val candidate = Qwen35RuntimeTuningProfiles.candidateForTier(Qwen35ModelTier.B0_8)
        val wrongIdentity = acceptanceFor(
            candidate = candidate,
            decision = Qwen35RuntimeAcceptanceDecision.PROMOTE_MEASURED,
            benchmarkEvidenceSha256 = setOf(SHA_A),
            lifecycleEvidenceSha256 = setOf(SHA_B),
            reviewedDeviceClasses = setOf("representative-arm64"),
            lifecycleValidated = true,
            memoryValidated = true,
            representativeCoverageValidated = true,
        ).copy(runtimeProfileId = "another-profile")

        assertThrows(IllegalArgumentException::class.java) {
            Qwen35RuntimeProfileAcceptance.reviewedProfileForTier(candidate.tier, wrongIdentity)
        }
    }

    @Test
    fun `promotion rejects malformed evidence digests`() {
        val candidate = Qwen35RuntimeTuningProfiles.candidateForTier(Qwen35ModelTier.B2)
        val malformed = acceptanceFor(
            candidate = candidate,
            decision = Qwen35RuntimeAcceptanceDecision.PROMOTE_MEASURED,
            benchmarkEvidenceSha256 = setOf("not-a-sha"),
            lifecycleEvidenceSha256 = setOf(SHA_B),
            reviewedDeviceClasses = setOf("representative-arm64"),
            lifecycleValidated = true,
            memoryValidated = true,
            representativeCoverageValidated = true,
        )

        assertThrows(IllegalArgumentException::class.java) {
            Qwen35RuntimeProfileAcceptance.reviewedProfileForTier(candidate.tier, malformed)
        }
    }

    private fun acceptanceFor(
        candidate: Qwen35RuntimeTuningProfile,
        decision: Qwen35RuntimeAcceptanceDecision,
        benchmarkEvidenceSha256: Set<String>,
        lifecycleEvidenceSha256: Set<String>,
        reviewedDeviceClasses: Set<String>,
        lifecycleValidated: Boolean,
        memoryValidated: Boolean,
        representativeCoverageValidated: Boolean,
    ) = Qwen35RuntimeAcceptanceEvidence(
        runtimeProfileId = candidate.id,
        runtimeProfileVersion = candidate.version,
        tier = candidate.tier,
        backendRevision = Qwen35RuntimeTuningProfiles.LLAMA_CPP_REVISION,
        benchmarkEvidenceSha256 = benchmarkEvidenceSha256,
        lifecycleEvidenceSha256 = lifecycleEvidenceSha256,
        reviewedDeviceClasses = reviewedDeviceClasses,
        lifecycleValidated = lifecycleValidated,
        memoryValidated = memoryValidated,
        representativeCoverageValidated = representativeCoverageValidated,
        decision = decision,
    )

    private companion object {
        const val SHA_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SHA_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
