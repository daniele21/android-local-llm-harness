package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ConsumerPreparationAction
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.models.ResolvedUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessApplicationsRuntimeSourceTest {
    @Test
    fun `inactive assignment does not observe runtime`() {
        val source = RuntimeGraphHarnessApplicationsRuntimeSource(
            activeResolved = { _, _ -> null },
            runtimeSnapshot = { error("Inactive assignment must not query runtime state") },
        )

        val summary = source.assignmentRuntime(applicationId(), useCaseId())

        assertFalse(summary.activationActive)
        assertEquals(ConsumerRuntimePhase.IDLE, summary.phase)
        assertEquals(ConsumerPreparationAction.NONE, summary.preparationAction)
    }

    @Test
    fun `active assignment projects loading preparation and exact execution`() {
        val resolved = resolvedUseCase()
        val source = source(
            resolved = resolved,
            runtime = RuntimeSnapshot(
                state = RuntimeState.PREPARING,
                loadedModel = null,
                activeSessions = 0,
                queuedRequests = 0,
            ),
        )

        val summary = source.assignmentRuntime(applicationId(), useCaseId())

        assertTrue(summary.activationActive)
        assertEquals(resolved.useCase.defaultPreset, summary.activePreset)
        assertEquals(resolved.model.id, summary.effectiveModelProfileId)
        assertEquals(ConsumerRuntimePhase.PREPARING, summary.phase)
        assertEquals(ConsumerPreparationAction.LOADING, summary.preparationAction)
    }

    @Test
    fun `active assignment distinguishes reuse and model switch preparation`() {
        val resolved = resolvedUseCase()
        val target = resolved.model.artifact.digest
        val reusing = source(
            resolved = resolved,
            runtime = RuntimeSnapshot(RuntimeState.PREPARING, target, activeSessions = 0, queuedRequests = 0),
        ).assignmentRuntime(applicationId(), useCaseId())
        val switching = source(
            resolved = resolved,
            runtime = RuntimeSnapshot(
                RuntimeState.PREPARING,
                ModelDigest("f".repeat(64)),
                activeSessions = 0,
                queuedRequests = 0,
            ),
        ).assignmentRuntime(applicationId(), useCaseId())

        assertEquals(ConsumerPreparationAction.REUSING, reusing.preparationAction)
        assertEquals(ConsumerPreparationAction.SWITCHING, switching.preparationAction)
    }

    @Test
    fun `ready and generating require the activated model to be resident`() {
        val resolved = resolvedUseCase()
        val target = resolved.model.artifact.digest
        val ready = source(
            resolved,
            RuntimeSnapshot(RuntimeState.READY, target, activeSessions = 1, queuedRequests = 0),
        ).assignmentRuntime(applicationId(), useCaseId())
        val generating = source(
            resolved,
            RuntimeSnapshot(RuntimeState.GENERATING, target, activeSessions = 1, queuedRequests = 0),
        ).assignmentRuntime(applicationId(), useCaseId())
        val unrelatedReady = source(
            resolved,
            RuntimeSnapshot(
                RuntimeState.READY,
                ModelDigest("e".repeat(64)),
                activeSessions = 1,
                queuedRequests = 0,
            ),
        ).assignmentRuntime(applicationId(), useCaseId())

        assertEquals(ConsumerRuntimePhase.READY, ready.phase)
        assertEquals(ConsumerRuntimePhase.GENERATING, generating.phase)
        assertEquals(ConsumerRuntimePhase.IDLE, unrelatedReady.phase)
    }

    @Test
    fun `failed shared runtime remains visible for an active assignment`() {
        val summary = source(
            resolvedUseCase(),
            RuntimeSnapshot(RuntimeState.FAILED, null, activeSessions = 0, queuedRequests = 0),
        ).assignmentRuntime(applicationId(), useCaseId())

        assertEquals(ConsumerRuntimePhase.FAILED, summary.phase)
    }

    private fun source(resolved: ResolvedUseCase, runtime: RuntimeSnapshot) = RuntimeGraphHarnessApplicationsRuntimeSource(
        activeResolved = { applicationId, useCaseId ->
            resolved.takeIf {
                it.binding.applicationId == applicationId && it.binding.useCaseId == useCaseId
            }
        },
        runtimeSnapshot = { runtime },
    )

    private fun resolvedUseCase(): ResolvedUseCase {
        val artifact = CuratedModelCatalog.releases.first().artifact
        val imported = ImportedPhoneModel(
            digest = artifact.digest,
            fileName = artifact.fileName,
            sizeBytes = artifact.sizeBytes,
            architecture = artifact.architecture,
            quantization = artifact.quantization,
        )
        return HarnessSharedRuntimeBindings.resolveOmbra(imported, applicationId())
    }

    private fun applicationId() = HarnessSharedRuntimeBindings.redactGuardApplicationId

    private fun useCaseId() = HarnessSharedRuntimeBindings.ombraUseCaseId
}
