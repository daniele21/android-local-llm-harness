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

        viewModel.models.attach(effects)

        assertEquals(distribution, viewModel.uiState.value.modelDistribution)
        assertSame(selected, viewModel.uiState.value.importedModel)
        assertEquals(selected.digest.sha256, viewModel.uiState.value.modelInventory.loadedDigest)
    }

    @Test
    fun catalogCommandsDelegateToAttachedEffects() {
        val effects = FakeModelEffects()
        val viewModel = HarnessViewModel()
        viewModel.models.attach(effects)

        val commands = listOf(
            ModelCatalogCommand.Refresh,
            ModelCatalogCommand.Download("release"),
            ModelCatalogCommand.CancelDownload("release"),
            ModelCatalogCommand.Install("release"),
            ModelCatalogCommand.VerifyInstalled("release"),
            ModelCatalogCommand.RequestRemoval("release"),
            ModelCatalogCommand.CancelRemoval("release"),
            ModelCatalogCommand.ConfirmRemoval("release"),
        )

        commands.forEach { command ->
            assertTrue(viewModel.models.executeCatalog(command))
        }
        assertEquals(commands, effects.catalogCommands)
    }

    @Test
    fun selectedModelCommandsRespectBusyState() {
        val selected = testModel("2")
        val effects = FakeModelEffects()
        val viewModel = HarnessViewModel(
            HarnessUiState(importedModel = selected, controllerBusy = true),
        )
        viewModel.models.attach(effects)

        assertFalse(viewModel.models.verifySelected())
        assertFalse(viewModel.models.requestSelectedRemoval())
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
        viewModel.models.attach(effects)

        assertTrue(viewModel.models.requestSelectedRemoval())
        assertTrue(viewModel.uiState.value.removalConfirmationPending)
        assertTrue(viewModel.models.confirmSelectedRemoval())
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
        viewModel.models.attach(effects)

        assertTrue(viewModel.models.selectInstalled(metadata))
        assertTrue(viewModel.models.verifySelected())
        assertSame(metadata, effects.selectedMetadata)
        assertEquals(listOf("select-installed", "verify-selected"), effects.commands)
    }

    @Test
    fun knownMismatchCanAdoptLoadedCatalogSelectionWithoutConfirmation() {
        val metadata = testMetadata("6")
        val loaded = HarnessModelInventoryItem(
            stableId = "release",
            displayName = metadata.displayName,
            origin = HarnessModelOrigin.CATALOG,
            digest = metadata.digest.sha256,
            lifecycle = HarnessModelLifecycle.DEGRADED,
            installed = true,
            loaded = true,
            degradation = HarnessModelDegradation.LOADED_MODEL_DIFFERS_FROM_SELECTION,
        )
        val distribution = PhoneModelDistributionState(
            models = listOf(
                PhoneCatalogModelUi(
                    stableId = "release",
                    displayName = metadata.displayName,
                    description = "test",
                    fileName = metadata.fileName,
                    sizeBytes = metadata.sizeBytes,
                    architecture = metadata.architecture,
                    quantization = metadata.quantization,
                    profileKey = metadata.profileKey,
                    licenseName = "Apache-2.0",
                    status = PhoneCatalogModelStatus.INSTALLED,
                    compatible = true,
                    compatibilityReasons = emptyList(),
                    compatibilityWarnings = emptyList(),
                    installedModel = metadata,
                ),
            ),
        )
        val effects = FakeModelEffects(
            current = ModelEffectsSnapshot(distribution, testModel("8"), metadata.digest.sha256),
        )
        val viewModel = HarnessViewModel()
        viewModel.models.attach(effects)
        val identity = HarnessModelDetails.identity(
            viewModel.uiState.value.modelInventory.items.single { it.loaded },
        )

        assertTrue(
            viewModel.models.recovery.request(
                identity,
                HarnessModelRecoveryAction.ADOPT_LOADED_SELECTION,
            ),
        )
        assertEquals(
            ModelRecoveryCommand.AdoptLoadedSelection(metadata),
            effects.recoveryCommands.single(),
        )
        assertEquals(null, viewModel.uiState.value.modelRecoveryConfirmation)
    }

    @Test
    fun runtimeReleaseRequiresConfirmationAndClearsPendingRequestWhenAccepted() {
        val digest = "7".repeat(64)
        val effects = FakeModelEffects(
            current = ModelEffectsSnapshot(PhoneModelDistributionState(), null, digest),
        )
        val viewModel = HarnessViewModel()
        viewModel.models.attach(effects)
        val runtime = viewModel.uiState.value.modelInventory.items.single()
        val identity = HarnessModelDetails.identity(runtime)

        assertTrue(
            viewModel.models.recovery.request(
                identity,
                HarnessModelRecoveryAction.RELEASE_RUNTIME,
            ),
        )
        assertEquals(
            HarnessModelRecoveryRequest(identity, HarnessModelRecoveryAction.RELEASE_RUNTIME),
            viewModel.uiState.value.modelRecoveryConfirmation,
        )
        assertTrue(viewModel.models.recovery.confirm())
        assertEquals(listOf(ModelRecoveryCommand.ReleaseRuntime), effects.recoveryCommands)
        assertEquals(null, viewModel.uiState.value.modelRecoveryConfirmation)
    }

    @Test
    fun detachedEffectsRejectCommands() {
        val effects = FakeModelEffects()
        val viewModel = HarnessViewModel()
        viewModel.models.attach(effects)
        viewModel.models.detach(effects)

        assertFalse(viewModel.models.executeCatalog(ModelCatalogCommand.Refresh))
        assertFalse(viewModel.models.executeCatalog(ModelCatalogCommand.Download("release")))
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
        val catalogCommands = mutableListOf<ModelCatalogCommand>()
        val commands = mutableListOf<String>()
        var selectedMetadata: InstalledCatalogModelMetadata? = null
        val recoveryCommands = mutableListOf<ModelRecoveryCommand>()

        override fun snapshot(): ModelEffectsSnapshot = current

        override fun executeCatalog(command: ModelCatalogCommand): Boolean {
            catalogCommands += command
            return true
        }

        override fun selectInstalled(metadata: InstalledCatalogModelMetadata): Boolean {
            selectedMetadata = metadata
            return record("select-installed")
        }

        override fun verifySelected(): Boolean = record("verify-selected")

        override fun removeSelected(): Boolean = record("remove-selected")

        override fun executeRecovery(command: ModelRecoveryCommand): Boolean {
            recoveryCommands += command
            return true
        }

        private fun record(command: String): Boolean {
            commands += command
            return true
        }
    }
}
