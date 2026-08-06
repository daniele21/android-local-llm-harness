package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessModelEffectsViewModelTest {
    @Test
    fun attachingEffectsPublishesDistributionSelectionAndRuntimeOwnership() {
        val selected = testModel("1")
        val distribution = PhoneModelDistributionState(message = "catalog ready")
        val effects = FakeModelEffects(
            current = ModelEffectsSnapshot(
                distribution = distribution,
                selectedModel = selected,
                loadedDigest = selected.digest.sha256,
            ),
        )
        val viewModel = HarnessViewModel()

        viewModel.attachModelEffects(effects)

        assertEquals(distribution, viewModel.uiState.value.modelDistribution)
        assertSame(selected, viewModel.uiState.value.importedModel)
        assertEquals(selected.digest.sha256, viewModel.uiState.value.modelInventory.loadedDigest)
    }

    @Test
    fun catalogCommandsDelegateToAttachedEffects() {
        val effects = FakeModelEffects()
        val viewModel = HarnessViewModel()
        viewModel.attachModelEffects(effects)

        assertTrue(viewModel.refreshModels())
        assertTrue(viewModel.downloadModel("release"))
        assertTrue(viewModel.cancelModelDownload("release"))
        assertTrue(viewModel.installModel("release"))
        assertTrue(viewModel.verifyInstalledModel("release"))
        assertTrue(viewModel.requestCatalogModelRemoval("release"))
        assertTrue(viewModel.cancelCatalogModelRemoval("release"))
        assertTrue(viewModel.confirmCatalogModelRemoval("release"))

        assertEquals(
            listOf(
                "refresh",
                "download:release",
                "cancel-download:release",
                "install:release",
                "verify-installed:release",
                "request-remove:release",
                "cancel-remove:release",
                "confirm-remove:release",
            ),
            effects.commands,
        )
    }

    @Test
    fun importAndSelectedModelCommandsRespectBusyState() {
        val selected = testModel("2")
        val effects = FakeModelEffects()
        val viewModel = HarnessViewModel(
            HarnessUiState(importedModel = selected, controllerBusy = true),
        )
        viewModel.attachModelEffects(effects)

        assertFalse(viewModel.requestModelImport())
        assertFalse(viewModel.verifySelectedModel())
        assertFalse(viewModel.requestSelectedModelRemoval())
        assertTrue(effects.commands.isEmpty())
    }

    @Test
    fun selectedRemovalRequiresConfirmationAndClearsItWhenAccepted() {
        val selected = testModel("3")
        val effects = FakeModelEffects(
            current = ModelEffectsSnapshot(
                distribution = PhoneModelDistributionState(),
                selectedModel = selected,
                loadedDigest = null,
            ),
        )
        val viewModel = HarnessViewModel()
        viewModel.attachModelEffects(effects)

        assertTrue(viewModel.requestSelectedModelRemoval())
        assertTrue(viewModel.uiState.value.removalConfirmationPending)
        assertTrue(viewModel.confirmSelectedModelRemoval())
        assertFalse(viewModel.uiState.value.removalConfirmationPending)
        assertEquals(listOf("remove-selected"), effects.commands)
    }

    @Test
    fun installedSelectionAndSelectedVerificationDelegate() {
        val selected = testModel("4")
        val metadata = testMetadata("5")
        val effects = FakeModelEffects(
            current = ModelEffectsSnapshot(
                distribution = PhoneModelDistributionState(),
                selectedModel = selected,
                loadedDigest = null,
            ),
        )
        val viewModel = HarnessViewModel()
        viewModel.attachModelEffects(effects)

        assertTrue(viewModel.selectInstalledModel(metadata))
        assertTrue(viewModel.verifySelectedModel())
        assertSame(metadata, effects.selectedMetadata)
        assertEquals(listOf("select-installed", "verify-selected"), effects.commands)
    }

    @Test
    fun detachedEffectsRejectCommands() {
        val effects = FakeModelEffects()
        val viewModel = HarnessViewModel()
        viewModel.attachModelEffects(effects)
        viewModel.detachModelEffects(effects)

        assertFalse(viewModel.refreshModels())
        assertFalse(viewModel.downloadModel("release"))
    }

    private fun testModel(seed: String): ImportedPhoneModel = ImportedPhoneModel(
        digest = ModelDigest(seed.repeat(64)),
        fileName = "model-$seed.gguf",
        sizeBytes = 1_024L,
        architecture = "qwen3",
        quantization = "Q4_K_M",
    )

    private fun testMetadata(seed: String): InstalledCatalogModelMetadata = InstalledCatalogModelMetadata(
        digest = ModelDigest(seed.repeat(64)),
        modelId = "model-$seed",
        version = "1.0.0",
        displayName = "Model $seed",
        profileKey = "profile-$seed",
        applicationId = "play-internal-phone-test",
        useCaseId = "manual-inference-playground",
        fileName = "model-$seed.gguf",
        sizeBytes = 2_048L,
        architecture = "qwen3",
        quantization = "Q4_K_M",
        installedAtEpochMs = 1L,
    )

    private class FakeModelEffects(
        private val current: ModelEffectsSnapshot = ModelEffectsSnapshot(
            distribution = PhoneModelDistributionState(),
            selectedModel = null,
            loadedDigest = null,
        ),
    ) : ModelEffects {
        val commands = mutableListOf<String>()
        var selectedMetadata: InstalledCatalogModelMetadata? = null

        override fun snapshot(): ModelEffectsSnapshot = current

        override fun requestImport(): Boolean = record("request-import")

        override fun refresh(): Boolean = record("refresh")

        override fun download(stableId: String): Boolean = record("download:$stableId")

        override fun cancelDownload(stableId: String): Boolean = record("cancel-download:$stableId")

        override fun install(stableId: String): Boolean = record("install:$stableId")

        override fun verifyInstalled(stableId: String): Boolean = record("verify-installed:$stableId")

        override fun requestCatalogRemoval(stableId: String): Boolean = record("request-remove:$stableId")

        override fun cancelCatalogRemoval(stableId: String): Boolean = record("cancel-remove:$stableId")

        override fun confirmCatalogRemoval(stableId: String): Boolean = record("confirm-remove:$stableId")

        override fun selectInstalled(metadata: InstalledCatalogModelMetadata): Boolean {
            selectedMetadata = metadata
            return record("select-installed")
        }

        override fun verifySelected(): Boolean = record("verify-selected")

        override fun removeSelected(): Boolean = record("remove-selected")

        private fun record(command: String): Boolean {
            commands += command
            return true
        }
    }
}
