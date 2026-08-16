package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextMemoryCostProfilesTest {
    private val runtime = ContextMemoryRuntimeIdentity("llama-cpp", "revision-a")
    private val model = ContextMemoryModelIdentity("profile-a", ModelDigest("a".repeat(64)))

    @Test
    fun `returns estimate only for exact model runtime and context identity`() {
        val estimator = estimator(
            profiles = listOf(profile(contextTokens = 4_096, source = MemoryCostSource.CANDIDATE)),
        )

        assertEquals("profile-a-4096", estimator.estimate("profile-a", 4_096)?.profileId)
        assertNull(estimator.estimate("profile-a", 2_048))
        assertNull(estimator.estimate("profile-b", 4_096))
    }

    @Test
    fun `rejects profile for different backend revision`() {
        val estimator = ContextMemoryCostProfileEstimator(
            profiles = listOf(profile(contextTokens = 4_096, source = MemoryCostSource.MEASURED)),
            runtimeIdentity = runtime.copy(backendRevision = "revision-b"),
            modelIdentityResolver = { model },
        )

        assertNull(estimator.estimate("profile-a", 4_096))
    }

    @Test
    fun `minimum provenance can require measured evidence`() {
        val candidateEstimator = estimator(
            profiles = listOf(profile(contextTokens = 4_096, source = MemoryCostSource.CANDIDATE)),
            minimumSource = MemoryCostSource.MEASURED,
        )
        val measuredEstimator = estimator(
            profiles = listOf(profile(contextTokens = 4_096, source = MemoryCostSource.MEASURED)),
            minimumSource = MemoryCostSource.MEASURED,
        )

        assertNull(candidateEstimator.estimate("profile-a", 4_096))
        assertEquals(MemoryCostSource.MEASURED, measuredEstimator.estimate("profile-a", 4_096)?.source)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate exact identities are rejected`() {
        estimator(
            profiles = listOf(
                profile(contextTokens = 4_096, source = MemoryCostSource.CANDIDATE, profileId = "one"),
                profile(contextTokens = 4_096, source = MemoryCostSource.MEASURED, profileId = "two"),
            ),
        )
    }

    private fun estimator(
        profiles: List<ContextMemoryCostProfile>,
        minimumSource: MemoryCostSource = MemoryCostSource.CANDIDATE,
    ): ContextMemoryCostProfileEstimator = ContextMemoryCostProfileEstimator(
        profiles = profiles,
        runtimeIdentity = runtime,
        modelIdentityResolver = { profileId -> model.takeIf { it.modelProfileId == profileId } },
        minimumSource = minimumSource,
    )

    private fun profile(
        contextTokens: Int,
        source: MemoryCostSource,
        profileId: String = "profile-a-$contextTokens",
    ): ContextMemoryCostProfile = ContextMemoryCostProfile(
        model = model,
        runtime = runtime,
        contextTokens = contextTokens,
        residentBytes = 256,
        peakIncrementalBytes = 512,
        source = source,
        profileId = profileId,
    )
}
