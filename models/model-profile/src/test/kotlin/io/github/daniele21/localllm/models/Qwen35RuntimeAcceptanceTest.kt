package io.github.daniele21.localllm.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Qwen35RuntimeAcceptanceTest {
    @Test
    fun `keep candidate never promotes a profile`() {
        val candidate = Qwen35RuntimeTuningProfiles.candidateForTier(Qwen35ModelTier.B0_8)
        val evidence = acceptanceFor(
            candidate,
            AcceptanceFixture(decision = Qwen35RuntimeAcceptanceDecision.KEEP_CANDIDATE),
        )

        val reviewed = Qwen35RuntimeProfileAcceptance.reviewedProfileForTier(candidate.tier, evidence)

        assertEquals(Qwen35RuntimeEvidenceStatus.CANDIDATE, reviewed.evidenceStatus)
        assertEquals(candidate, reviewed)
    }

    @Test
    fun `measured promotion requires every acceptance gate`() {
        val candidate = Qwen35RuntimeTuningProfiles.candidateForTier(Qwen35ModelTier.B2)
        val incomplete = acceptanceFor(
            candidate,
            completePromotionFixture().copy(memoryValidated = false),
        )

        assertThrows(IllegalArgumentException::class.java) {
            Qwen35RuntimeProfileAcceptance.reviewedProfileForTier(candidate.tier, incomplete)
        }
    }

    @Test
    fun `measured promotion is explicit and provenance bound`() {
        val candidate = Qwen35RuntimeTuningProfiles.candidateForTier(Qwen35ModelTier.B2)
        val evidence = acceptanceFor(
            candidate,
            completePromotionFixture(
                reviewedDeviceClasses = setOf("representative-arm64-a", "representative-arm64-b"),
            ),
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
        val wrongIdentity = acceptanceFor(candidate, completePromotionFixture())
            .copy(runtimeProfileId = "another-profile")

        assertThrows(IllegalArgumentException::class.java) {
            Qwen35RuntimeProfileAcceptance.reviewedProfileForTier(candidate.tier, wrongIdentity)
        }
    }

    @Test
    fun `promotion rejects malformed evidence digests`() {
        val candidate = Qwen35RuntimeTuningProfiles.candidateForTier(Qwen35ModelTier.B2)
        val malformed = acceptanceFor(
            candidate,
            completePromotionFixture().copy(benchmarkEvidenceSha256 = setOf("not-a-sha")),
        )

        assertThrows(IllegalArgumentException::class.java) {
            Qwen35RuntimeProfileAcceptance.reviewedProfileForTier(candidate.tier, malformed)
        }
    }

    private fun acceptanceFor(
        candidate: Qwen35RuntimeTuningProfile,
        fixture: AcceptanceFixture,
    ) = Qwen35RuntimeAcceptanceEvidence(
        runtimeProfileId = candidate.id,
        runtimeProfileVersion = candidate.version,
        tier = candidate.tier,
        backendRevision = Qwen35RuntimeTuningProfiles.LLAMA_CPP_REVISION,
        benchmarkEvidenceSha256 = fixture.benchmarkEvidenceSha256,
        lifecycleEvidenceSha256 = fixture.lifecycleEvidenceSha256,
        reviewedDeviceClasses = fixture.reviewedDeviceClasses,
        lifecycleValidated = fixture.lifecycleValidated,
        memoryValidated = fixture.memoryValidated,
        representativeCoverageValidated = fixture.representativeCoverageValidated,
        decision = fixture.decision,
    )

    private fun completePromotionFixture(
        reviewedDeviceClasses: Set<String> = setOf("representative-arm64"),
    ) = AcceptanceFixture(
        decision = Qwen35RuntimeAcceptanceDecision.PROMOTE_MEASURED,
        benchmarkEvidenceSha256 = setOf(SHA_A),
        lifecycleEvidenceSha256 = setOf(SHA_B),
        reviewedDeviceClasses = reviewedDeviceClasses,
        lifecycleValidated = true,
        memoryValidated = true,
        representativeCoverageValidated = true,
    )

    private data class AcceptanceFixture(
        val decision: Qwen35RuntimeAcceptanceDecision,
        val benchmarkEvidenceSha256: Set<String> = emptySet(),
        val lifecycleEvidenceSha256: Set<String> = emptySet(),
        val reviewedDeviceClasses: Set<String> = emptySet(),
        val lifecycleValidated: Boolean = false,
        val memoryValidated: Boolean = false,
        val representativeCoverageValidated: Boolean = false,
    )

    private companion object {
        const val SHA_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SHA_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
