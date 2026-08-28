package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ConsumerPreparationAction
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone

internal fun HarnessAssignmentRuntimeSummary.runtimeLabel(): String = if (!activationActive) {
    "Inactive"
} else {
    phase.runtimePhaseLabel()
}

internal fun HarnessAssignmentRuntimeSummary.runtimeTone(): HarnessStatusTone = if (!activationActive) {
    HarnessStatusTone.NEUTRAL
} else {
    when (phase) {
        ConsumerRuntimePhase.IDLE -> HarnessStatusTone.INFO
        ConsumerRuntimePhase.PREPARING -> HarnessStatusTone.INFO
        ConsumerRuntimePhase.READY -> HarnessStatusTone.SUCCESS
        ConsumerRuntimePhase.GENERATING -> HarnessStatusTone.INFO
        ConsumerRuntimePhase.FAILED -> HarnessStatusTone.ERROR
    }
}

internal fun ConsumerRuntimePhase.runtimePhaseLabel(): String = when (this) {
    ConsumerRuntimePhase.IDLE -> "Activated"
    ConsumerRuntimePhase.PREPARING -> "Preparing"
    ConsumerRuntimePhase.READY -> "Ready"
    ConsumerRuntimePhase.GENERATING -> "Generating"
    ConsumerRuntimePhase.FAILED -> "Failed"
}

internal fun ConsumerPreparationAction.preparationLabel(): String = when (this) {
    ConsumerPreparationAction.NONE -> "None"
    ConsumerPreparationAction.LOADING -> "Loading model"
    ConsumerPreparationAction.REUSING -> "Reusing loaded model"
    ConsumerPreparationAction.SWITCHING -> "Switching model"
}
