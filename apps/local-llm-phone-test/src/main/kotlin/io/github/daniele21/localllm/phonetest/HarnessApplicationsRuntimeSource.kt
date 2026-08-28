package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerPreparationAction
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ResolvedUseCase

internal data class HarnessAssignmentRuntimeSummary(
    val activationActive: Boolean,
    val activePreset: InferencePresetRef? = null,
    val effectiveModelProfileId: String? = null,
    val phase: ConsumerRuntimePhase = ConsumerRuntimePhase.IDLE,
    val preparationAction: ConsumerPreparationAction = ConsumerPreparationAction.NONE,
)

internal fun interface HarnessApplicationsRuntimeSource {
    fun assignmentRuntime(applicationId: ApplicationId, useCaseId: UseCaseId): HarnessAssignmentRuntimeSummary
}

internal object NoHarnessApplicationsRuntimeSource : HarnessApplicationsRuntimeSource {
    override fun assignmentRuntime(applicationId: ApplicationId, useCaseId: UseCaseId) = HarnessAssignmentRuntimeSummary(
        activationActive = false,
    )
}

internal class RuntimeGraphHarnessApplicationsRuntimeSource(
    private val activeResolved: (ApplicationId, UseCaseId) -> ResolvedUseCase?,
    private val runtimeSnapshot: () -> RuntimeSnapshot?,
) : HarnessApplicationsRuntimeSource {
    constructor(runtimeGraph: HarnessRuntimeGraph) : this(
        activeResolved = runtimeGraph::activeConsumerResolved,
        runtimeSnapshot = runtimeGraph::runtimeSnapshot,
    )

    override fun assignmentRuntime(applicationId: ApplicationId, useCaseId: UseCaseId): HarnessAssignmentRuntimeSummary {
        val resolved = activeResolved(applicationId, useCaseId)
            ?: return HarnessAssignmentRuntimeSummary(activationActive = false)
        val runtime = runtimeSnapshot()
        val targetModel = resolved.model.artifact.digest
        val phase = runtime.phaseFor(targetModel)
        return HarnessAssignmentRuntimeSummary(
            activationActive = true,
            activePreset = resolved.useCase.defaultPreset,
            effectiveModelProfileId = resolved.model.id,
            phase = phase,
            preparationAction = if (phase == ConsumerRuntimePhase.PREPARING) {
                runtime.preparationActionFor(targetModel)
            } else {
                ConsumerPreparationAction.NONE
            },
        )
    }
}

private fun RuntimeSnapshot?.phaseFor(targetModel: io.github.daniele21.localllm.contracts.ModelDigest): ConsumerRuntimePhase =
    when (this?.state ?: RuntimeState.IDLE) {
        RuntimeState.IDLE -> ConsumerRuntimePhase.IDLE
        RuntimeState.PREPARING -> ConsumerRuntimePhase.PREPARING
        RuntimeState.READY -> if (loadedModel == targetModel) ConsumerRuntimePhase.READY else ConsumerRuntimePhase.IDLE
        RuntimeState.GENERATING -> if (loadedModel == targetModel) ConsumerRuntimePhase.GENERATING else ConsumerRuntimePhase.IDLE
        RuntimeState.DEGRADED,
        RuntimeState.FAILED,
        -> ConsumerRuntimePhase.FAILED
    }

private fun RuntimeSnapshot?.preparationActionFor(
    targetModel: io.github.daniele21.localllm.contracts.ModelDigest,
): ConsumerPreparationAction = when (this?.loadedModel) {
    null -> ConsumerPreparationAction.LOADING
    targetModel -> ConsumerPreparationAction.REUSING
    else -> ConsumerPreparationAction.SWITCHING
}
