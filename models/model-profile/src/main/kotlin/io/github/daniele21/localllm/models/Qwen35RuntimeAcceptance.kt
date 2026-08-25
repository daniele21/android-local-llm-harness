package io.github.daniele21.localllm.models

enum class Qwen35RuntimeAcceptanceDecision {
    KEEP_CANDIDATE,
    PROMOTE_MEASURED,
}

data class Qwen35RuntimeAcceptanceEvidence(
    val runtimeProfileId: String,
    val runtimeProfileVersion: Int,
    val tier: Qwen35ModelTier,
    val backendRevision: String,
    val benchmarkEvidenceSha256: Set<String>,
    val lifecycleEvidenceSha256: Set<String>,
    val reviewedDeviceClasses: Set<String>,
    val lifecycleValidated: Boolean,
    val memoryValidated: Boolean,
    val representativeCoverageValidated: Boolean,
    val decision: Qwen35RuntimeAcceptanceDecision,
) {
    fun applyTo(candidate: Qwen35RuntimeTuningProfile): Qwen35RuntimeTuningProfile {
        require(candidate.id == runtimeProfileId) {
            "Acceptance evidence targets runtime profile $runtimeProfileId, not ${candidate.id}"
        }
        require(candidate.version == runtimeProfileVersion) {
            "Acceptance evidence targets runtime profile version $runtimeProfileVersion, not ${candidate.version}"
        }
        require(candidate.tier == tier) {
            "Acceptance evidence tier $tier does not match ${candidate.tier}"
        }
        require(backendRevision == Qwen35RuntimeTuningProfiles.LLAMA_CPP_REVISION) {
            "Acceptance evidence backend revision is not the production llama.cpp pin"
        }
        require(candidate.evidenceStatus == Qwen35RuntimeEvidenceStatus.CANDIDATE) {
            "Only CANDIDATE runtime profiles can pass the acceptance gate"
        }

        if (decision == Qwen35RuntimeAcceptanceDecision.KEEP_CANDIDATE) {
            return candidate
        }

        require(benchmarkEvidenceSha256.isNotEmpty()) {
            "MEASURED promotion requires benchmark evidence identity"
        }
        require(lifecycleEvidenceSha256.isNotEmpty()) {
            "MEASURED promotion requires lifecycle evidence identity"
        }
        require(benchmarkEvidenceSha256.all(::isSha256) && lifecycleEvidenceSha256.all(::isSha256)) {
            "Acceptance evidence digests must be lowercase SHA-256 values"
        }
        require(reviewedDeviceClasses.isNotEmpty() && reviewedDeviceClasses.none(String::isBlank)) {
            "MEASURED promotion requires reviewed representative-device coverage"
        }
        require(lifecycleValidated) {
            "MEASURED promotion requires lifecycle acceptance"
        }
        require(memoryValidated) {
            "MEASURED promotion requires memory acceptance"
        }
        require(representativeCoverageValidated) {
            "MEASURED promotion requires explicit representative-device coverage acceptance"
        }

        return candidate.copy(evidenceStatus = Qwen35RuntimeEvidenceStatus.MEASURED)
    }

    private fun isSha256(value: String): Boolean = SHA256_REGEX.matches(value)

    private companion object {
        val SHA256_REGEX = Regex("[0-9a-f]{64}")
    }
}

object Qwen35RuntimeProfileAcceptance {
    fun reviewedProfileForTier(
        tier: Qwen35ModelTier,
        evidence: Qwen35RuntimeAcceptanceEvidence,
    ): Qwen35RuntimeTuningProfile = evidence.applyTo(Qwen35RuntimeTuningProfiles.candidateForTier(tier))
}
