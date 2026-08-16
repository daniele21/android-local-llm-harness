package io.github.daniele21.localllm.runtime

typealias ModelLoadMemoryRuntimeIdentity = ContextMemoryRuntimeIdentity
typealias ModelLoadMemoryModelIdentity = ContextMemoryModelIdentity

data class ModelLoadMemoryCostProfile(
    val model: ModelLoadMemoryModelIdentity,
    val runtime: ModelLoadMemoryRuntimeIdentity,
    val residentBytes: Long,
    val peakIncrementalBytes: Long,
    val source: MemoryCostSource,
    val profileId: String,
) {
    init {
        require(residentBytes >= 0L) { "Resident bytes must not be negative" }
        require(peakIncrementalBytes >= residentBytes) {
            "Peak incremental bytes must be at least the resident estimate"
        }
        require(profileId.isNotBlank()) { "Memory cost profile ID must not be blank" }
    }

    fun estimate(): MemoryCostEstimate = MemoryCostEstimate(
        residentBytes = residentBytes,
        peakIncrementalBytes = peakIncrementalBytes,
        source = source,
        profileId = profileId,
    )
}

class ModelLoadMemoryCostProfileEstimator(
    profiles: Collection<ModelLoadMemoryCostProfile>,
    private val runtimeIdentity: ModelLoadMemoryRuntimeIdentity,
    private val modelIdentityResolver: (modelProfileId: String) -> ModelLoadMemoryModelIdentity?,
    private val minimumSource: MemoryCostSource = MemoryCostSource.CANDIDATE,
) : ModelMemoryCostEstimator {
    private val profilesByKey = profiles.associateBy { profile -> ProfileKey(profile.model, profile.runtime) }

    init {
        require(profilesByKey.size == profiles.size) { "Duplicate model load memory cost profile identity" }
    }

    override fun estimate(modelProfileId: String): MemoryCostEstimate? {
        val model = modelIdentityResolver(modelProfileId) ?: return null
        val profile = profilesByKey[ProfileKey(model, runtimeIdentity)] ?: return null
        return profile.takeIf { it.source.isAtLeast(minimumSource) }?.estimate()
    }

    fun hasCompatibleProfile(modelProfileId: String): Boolean = estimate(modelProfileId) != null

    private fun MemoryCostSource.isAtLeast(required: MemoryCostSource): Boolean = ordinal >= required.ordinal

    private data class ProfileKey(val model: ModelLoadMemoryModelIdentity, val runtime: ModelLoadMemoryRuntimeIdentity)
}
