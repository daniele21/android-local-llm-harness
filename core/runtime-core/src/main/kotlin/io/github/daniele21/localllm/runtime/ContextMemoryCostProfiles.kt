package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest

data class ContextMemoryRuntimeIdentity(
    val backendId: String,
    val backendRevision: String?,
) {
    init {
        require(backendId.isNotBlank()) { "Backend ID must not be blank" }
    }
}

data class ContextMemoryModelIdentity(
    val modelProfileId: String,
    val modelDigest: ModelDigest,
) {
    init {
        require(modelProfileId.isNotBlank()) { "Model profile ID must not be blank" }
    }
}

data class ContextMemoryCostProfile(
    val model: ContextMemoryModelIdentity,
    val runtime: ContextMemoryRuntimeIdentity,
    val contextTokens: Int,
    val residentBytes: Long,
    val peakIncrementalBytes: Long,
    val source: MemoryCostSource,
    val profileId: String,
) {
    init {
        require(contextTokens > 0) { "Context tokens must be positive" }
        require(residentBytes >= 0L) { "Resident bytes must not be negative" }
        require(peakIncrementalBytes >= 0L) { "Peak incremental bytes must not be negative" }
        require(profileId.isNotBlank()) { "Memory cost profile ID must not be blank" }
    }

    fun estimate(): MemoryCostEstimate = MemoryCostEstimate(
        residentBytes = residentBytes,
        peakIncrementalBytes = peakIncrementalBytes,
        source = source,
        profileId = profileId,
    )
}

class ContextMemoryCostProfileEstimator(
    profiles: Collection<ContextMemoryCostProfile>,
    private val runtimeIdentity: ContextMemoryRuntimeIdentity,
    private val modelIdentityResolver: (modelProfileId: String) -> ContextMemoryModelIdentity?,
    private val minimumSource: MemoryCostSource = MemoryCostSource.CANDIDATE,
) : ContextMemoryCostEstimator {
    private val profilesByKey = profiles.associateBy { profile ->
        ProfileKey(profile.model, profile.runtime, profile.contextTokens)
    }

    init {
        require(profilesByKey.size == profiles.size) { "Duplicate context memory cost profile identity" }
    }

    override fun estimate(modelProfileId: String, contextTokens: Int): MemoryCostEstimate? {
        val model = modelIdentityResolver(modelProfileId) ?: return null
        val profile = profilesByKey[ProfileKey(model, runtimeIdentity, contextTokens)] ?: return null
        return profile.takeIf { it.source.isAtLeast(minimumSource) }?.estimate()
    }

    fun hasCompatibleProfile(modelProfileId: String, contextTokens: Int): Boolean = estimate(modelProfileId, contextTokens) != null

    private fun MemoryCostSource.isAtLeast(required: MemoryCostSource): Boolean = ordinal >= required.ordinal

    private data class ProfileKey(
        val model: ContextMemoryModelIdentity,
        val runtime: ContextMemoryRuntimeIdentity,
        val contextTokens: Int,
    )
}
