package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelLoadMemoryCostProfilesTest {
    @Test
    fun `returns exact compatible model and runtime profile`() {
        val estimator = estimator(listOf(profile(source = MemoryCostSource.MEASURED)))

        val estimate = estimator.estimate(MODEL_PROFILE_ID)

        assertEquals(512L, estimate?.residentBytes)
        assertEquals(768L, estimate?.peakIncrementalBytes)
        assertEquals(MemoryCostSource.MEASURED, estimate?.source)
        assertEquals(PROFILE_ID, estimate?.profileId)
    }

    @Test
    fun `fails closed for model digest mismatch`() {
        val estimator = estimator(
            listOf(profile(modelDigest = ModelDigest("b".repeat(64)))),
        )

        assertNull(estimator.estimate(MODEL_PROFILE_ID))
    }

    @Test
    fun `fails closed for backend revision mismatch`() {
        val estimator = ModelLoadMemoryCostProfileEstimator(
            profiles = listOf(profile()),
            runtimeIdentity = ModelLoadMemoryRuntimeIdentity(BACKEND_ID, "revision-2"),
            modelIdentityResolver = ::resolveModel,
        )

        assertNull(estimator.estimate(MODEL_PROFILE_ID))
    }

    @Test
    fun `minimum provenance rejects candidate when measured is required`() {
        val estimator = estimator(
            profiles = listOf(profile(source = MemoryCostSource.CANDIDATE)),
            minimumSource = MemoryCostSource.MEASURED,
        )

        assertNull(estimator.estimate(MODEL_PROFILE_ID))
    }

    @Test
    fun `duplicate exact identities are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            estimator(listOf(profile(profileId = "one"), profile(profileId = "two")))
        }
    }

    private fun estimator(
        profiles: List<ModelLoadMemoryCostProfile>,
        minimumSource: MemoryCostSource = MemoryCostSource.CANDIDATE,
    ): ModelLoadMemoryCostProfileEstimator = ModelLoadMemoryCostProfileEstimator(
        profiles = profiles,
        runtimeIdentity = ModelLoadMemoryRuntimeIdentity(BACKEND_ID, BACKEND_REVISION),
        modelIdentityResolver = ::resolveModel,
        minimumSource = minimumSource,
    )

    private fun resolveModel(modelProfileId: String): ModelLoadMemoryModelIdentity? =
        ModelLoadMemoryModelIdentity(MODEL_PROFILE_ID, MODEL_DIGEST).takeIf { modelProfileId == MODEL_PROFILE_ID }

    private fun profile(
        modelDigest: ModelDigest = MODEL_DIGEST,
        source: MemoryCostSource = MemoryCostSource.CANDIDATE,
        profileId: String = PROFILE_ID,
    ): ModelLoadMemoryCostProfile = ModelLoadMemoryCostProfile(
        model = ModelLoadMemoryModelIdentity(MODEL_PROFILE_ID, modelDigest),
        runtime = ModelLoadMemoryRuntimeIdentity(BACKEND_ID, BACKEND_REVISION),
        residentBytes = 512L,
        peakIncrementalBytes = 768L,
        source = source,
        profileId = profileId,
    )

    private companion object {
        const val MODEL_PROFILE_ID = "qwen-profile"
        const val BACKEND_ID = "llama-cpp"
        const val BACKEND_REVISION = "rev-1"
        const val PROFILE_ID = "qwen-load-measured-v1"
        val MODEL_DIGEST = ModelDigest("a".repeat(64))
    }
}
