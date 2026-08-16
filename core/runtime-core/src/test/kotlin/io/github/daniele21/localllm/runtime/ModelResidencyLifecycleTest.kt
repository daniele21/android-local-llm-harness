package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelResidencyLifecycleTest {
    private val firstDigest = ModelDigest("a".repeat(64))
    private val secondDigest = ModelDigest("b".repeat(64))

    @Test
    fun `starts not resident without a handle`() {
        val lifecycle = ModelResidencyLifecycle()

        assertEquals(ModelResidencyState.NOT_RESIDENT, lifecycle.snapshot().state)
        assertNull(lifecycle.residentModelOrNull())
        assertNull(lifecycle.reusableModelOrNull())
    }

    @Test
    fun `successful load transitions through loading to resident`() {
        val lifecycle = ModelResidencyLifecycle()
        val model = residentModel("profile-a", firstDigest)

        lifecycle.beginLoad(model.profileId, firstDigest)
        val loading = lifecycle.snapshot()
        assertEquals(ModelResidencyState.LOADING, loading.state)
        assertEquals(model.profileId, loading.loadingProfileId)
        assertEquals(firstDigest, loading.loadingDigest)
        assertNull(lifecycle.residentModelOrNull())

        lifecycle.loadSucceeded(model)

        assertEquals(ModelResidencyState.RESIDENT, lifecycle.snapshot().state)
        assertSame(model, lifecycle.reusableModelOrNull())
        assertSame(model, lifecycle.residentModelOrNull())
    }

    @Test
    fun `failed load rolls back to not resident`() {
        val lifecycle = ModelResidencyLifecycle()

        lifecycle.beginLoad("profile-a", firstDigest)
        lifecycle.loadFailed()

        assertEquals(ModelResidencyState.NOT_RESIDENT, lifecycle.snapshot().state)
        assertNull(lifecycle.residentModelOrNull())
    }

    @Test
    fun `unload keeps physical handle visible until success`() {
        val lifecycle = residentLifecycle("profile-a", firstDigest)
        val model = checkNotNull(lifecycle.reusableModelOrNull())

        assertSame(model, lifecycle.beginUnload())
        assertEquals(ModelResidencyState.UNLOADING, lifecycle.snapshot().state)
        assertNull(lifecycle.reusableModelOrNull())
        assertSame(model, lifecycle.residentModelOrNull())
        assertNull(lifecycle.beginUnload())

        lifecycle.unloadSucceeded()

        assertEquals(ModelResidencyState.NOT_RESIDENT, lifecycle.snapshot().state)
        assertNull(lifecycle.residentModelOrNull())
        assertNull(lifecycle.beginUnload())
    }

    @Test
    fun `failed unload rolls back to the same resident handle`() {
        val lifecycle = residentLifecycle("profile-a", firstDigest)
        val model = checkNotNull(lifecycle.reusableModelOrNull())

        assertSame(model, lifecycle.beginUnload())
        lifecycle.unloadFailed()

        assertEquals(ModelResidencyState.RESIDENT, lifecycle.snapshot().state)
        assertSame(model, lifecycle.reusableModelOrNull())
    }

    @Test
    fun `cannot begin a second load while a model is resident`() {
        val lifecycle = residentLifecycle("profile-a", firstDigest)

        val failure = runCatching { lifecycle.beginLoad("profile-b", secondDigest) }

        assertTrue(failure.isFailure)
        assertEquals(ModelResidencyState.RESIDENT, lifecycle.snapshot().state)
        assertEquals(firstDigest, lifecycle.reusableModelOrNull()?.handle?.digest)
    }

    private fun residentLifecycle(profileId: String, digest: ModelDigest): ModelResidencyLifecycle =
        ModelResidencyLifecycle().also { lifecycle ->
            val model = residentModel(profileId, digest)
            lifecycle.beginLoad(profileId, digest)
            lifecycle.loadSucceeded(model)
        }

    private fun residentModel(profileId: String, digest: ModelDigest): ResidentModel =
        ResidentModel(profileId, TestModelHandle(digest, profileId))
}

private data class TestModelHandle(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 1,
) : BackendModelHandle
