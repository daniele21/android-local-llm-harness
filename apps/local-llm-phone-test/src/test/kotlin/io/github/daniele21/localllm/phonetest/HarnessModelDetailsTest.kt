package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessModelDetailsTest {
    @Test
    fun `identity prefers digest and falls back to stable catalog identity`() {
        val digestItem = item(stableId = "catalog", digest = "a".repeat(64))
        val catalogOnly = item(stableId = "qwen/release", digest = null)

        assertEquals("digest:${"a".repeat(64)}", HarnessModelDetails.identity(digestItem))
        assertEquals("stable:qwen/release", HarnessModelDetails.identity(catalogOnly))
    }

    @Test
    fun `detail presentation resolves compatibility integrity selection and runtime ownership`() {
        val model = item(
            stableId = "catalog",
            digest = "b".repeat(64),
            lifecycle = HarnessModelLifecycle.LOADED,
            installed = true,
            selected = true,
            loaded = true,
        )
        val inventory = HarnessModelInventoryState(
            items = listOf(model),
            selectedDigest = model.digest,
            loadedDigest = model.digest,
        )

        val detail = HarnessModelDetails.present(inventory, HarnessModelDetails.identity(model))

        requireNotNull(detail)
        assertEquals("Compatible", detail.compatibility)
        assertEquals("Digest recorded", detail.integrity)
        assertEquals("Installed", detail.installation)
        assertEquals("Selected for this app", detail.selection)
        assertEquals("Owned by runtime", detail.runtimeOwnership)
        assertTrue(detail.recoveryOptions.isEmpty())
    }

    @Test
    fun `known mismatch offers adoption and confirmed runtime release`() {
        val model = item(
            stableId = "catalog",
            digest = "c".repeat(64),
            lifecycle = HarnessModelLifecycle.DEGRADED,
            installed = true,
            loaded = true,
            degradation = HarnessModelDegradation.LOADED_MODEL_DIFFERS_FROM_SELECTION,
        )
        val detail = HarnessModelDetails.present(
            HarnessModelInventoryState(items = listOf(model), loadedDigest = model.digest),
            HarnessModelDetails.identity(model),
        )

        requireNotNull(detail)
        assertEquals(
            listOf(
                HarnessModelRecoveryAction.ADOPT_LOADED_SELECTION,
                HarnessModelRecoveryAction.RELEASE_RUNTIME,
            ),
            detail.recoveryOptions.map(HarnessModelRecoveryOption::action),
        )
        assertFalse(detail.recoveryOptions.first().requiresConfirmation)
        assertTrue(detail.recoveryOptions.last().requiresConfirmation)
    }

    @Test
    fun `unknown runtime ownership only offers release and malformed identity is unavailable`() {
        val runtime = item(
            stableId = "runtime::digest",
            digest = "d".repeat(64),
            origin = HarnessModelOrigin.RUNTIME,
            lifecycle = HarnessModelLifecycle.DEGRADED,
            loaded = true,
            degradation = HarnessModelDegradation.LOADED_MODEL_NOT_IN_INVENTORY,
        )
        val inventory = HarnessModelInventoryState(items = listOf(runtime), loadedDigest = runtime.digest)

        val detail = HarnessModelDetails.present(inventory, HarnessModelDetails.identity(runtime))

        requireNotNull(detail)
        assertEquals(
            listOf(HarnessModelRecoveryAction.RELEASE_RUNTIME),
            detail.recoveryOptions.map(HarnessModelRecoveryOption::action),
        )
        assertNull(HarnessModelDetails.present(inventory, "unknown"))
    }

    @Suppress("LongParameterList")
    private fun item(
        stableId: String,
        digest: String?,
        origin: HarnessModelOrigin = HarnessModelOrigin.CATALOG,
        lifecycle: HarnessModelLifecycle = HarnessModelLifecycle.INSTALLED,
        installed: Boolean = false,
        selected: Boolean = false,
        loaded: Boolean = false,
        degradation: HarnessModelDegradation? = null,
    ): HarnessModelInventoryItem = HarnessModelInventoryItem(
        stableId = stableId,
        displayName = "Qwen model",
        origin = origin,
        digest = digest,
        sizeBytes = 1_048_576L,
        architecture = "qwen3",
        quantization = "Q4_K_M",
        lifecycle = lifecycle,
        installed = installed,
        selected = selected,
        loaded = loaded,
        degradation = degradation,
    )
}
