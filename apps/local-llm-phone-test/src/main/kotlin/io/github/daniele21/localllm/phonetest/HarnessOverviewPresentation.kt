package io.github.daniele21.localllm.phonetest

internal enum class HarnessOverviewPrimaryAction {
    CHOOSE_MODEL,
    RUN_PROMPT,
    RESOLVE_MODEL_STATE,
}

internal data class HarnessOverviewPresentation(
    val heroLabel: String,
    val heroTitle: String,
    val heroDetail: String,
    val primaryAction: HarnessOverviewPrimaryAction,
    val selectedModelValue: String,
    val selectedModelStatus: String,
    val selectedModelPositive: Boolean,
    val runtimeValue: String,
    val residencyValue: String,
    val residencyStatus: String,
    val residencyPositive: Boolean,
    val healthValue: String,
    val healthStatus: String,
    val healthPositive: Boolean,
    val processPss: String,
    val thermalStatus: String,
    val latestRunValue: String,
    val latestRunStatus: String,
    val latestRunPositive: Boolean,
)

internal fun harnessOverviewPresentation(
    state: HarnessUiState,
    diagnostics: DiagnosticsUiState,
    processPss: String?,
    thermalStatus: String?,
): HarnessOverviewPresentation {
    val selectedModel = state.importedModel
    val selectedDigest = selectedModel?.digest?.sha256
    val loadedDigest = state.modelInventory.loadedDigest
    val mismatch = state.modelInventory.degradedCount > 0 ||
        (selectedDigest != null && loadedDigest != null && selectedDigest != loadedDigest)
    val selectedResident = selectedDigest != null && selectedDigest == loadedDigest

    val primaryAction = when {
        selectedModel == null -> HarnessOverviewPrimaryAction.CHOOSE_MODEL
        mismatch -> HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE
        else -> HarnessOverviewPrimaryAction.RUN_PROMPT
    }

    val heroLabel = when (primaryAction) {
        HarnessOverviewPrimaryAction.CHOOSE_MODEL -> "MODEL REQUIRED"
        HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE -> "MODEL STATE NEEDS ATTENTION"
        HarnessOverviewPrimaryAction.RUN_PROMPT -> "READY TO EVALUATE"
    }
    val heroTitle = when (primaryAction) {
        HarnessOverviewPrimaryAction.CHOOSE_MODEL -> "Choose a model to begin"
        HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE -> "Reconcile model state before the next run"
        HarnessOverviewPrimaryAction.RUN_PROMPT -> selectedModel?.fileName ?: "Ready to evaluate"
    }
    val heroDetail = when {
        primaryAction == HarnessOverviewPrimaryAction.CHOOSE_MODEL ->
            "Choose a reviewed Qwen3.5 model for this device, then run a prompt with a measured configuration."

        primaryAction == HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE ->
            "The selected model and runtime residency do not agree. Review Models before running another inference."

        selectedResident ->
            "Selected and currently resident in memory. The next run can reuse the local runtime when policy allows."

        else ->
            "Selected on this device. The runtime will load it explicitly when the next inference requires it."
    }

    val selectedModelValue = state.modelInventory.selectedItem?.displayName ?: selectedModel?.fileName ?: "No model selected"
    val selectedModelStatus = when {
        selectedModel == null -> "Not selected"
        mismatch -> "Needs attention"
        selectedResident -> "In memory"
        else -> "Selected"
    }
    val runtimeValue = diagnostics.runtime?.state?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Unavailable"
    val residencyValue = loadedDigest?.let { "${it.take(12)}…" } ?: "No model in memory"
    val residencyStatus = when {
        state.playground.active -> "Running"
        mismatch -> "Mismatch"
        selectedResident -> "Resident"
        loadedDigest != null -> "Resident"
        else -> "Not resident"
    }
    val healthValue = diagnostics.healthStatus
    val healthPositive = diagnostics.healthStatus.equals("Pass", ignoreCase = true)
    val latestMetrics = state.playground.metrics
    val latestRunValue = latestMetrics?.totalMs?.let { "$it ms total" } ?: "No runs yet"
    val latestRunStatus = if (latestMetrics == null) {
        "Not run"
    } else {
        state.playground.phase.name.lowercase().replaceFirstChar(Char::uppercase)
    }

    return HarnessOverviewPresentation(
        heroLabel = heroLabel,
        heroTitle = heroTitle,
        heroDetail = heroDetail,
        primaryAction = primaryAction,
        selectedModelValue = selectedModelValue,
        selectedModelStatus = selectedModelStatus,
        selectedModelPositive = selectedModel != null && !mismatch,
        runtimeValue = runtimeValue,
        residencyValue = residencyValue,
        residencyStatus = residencyStatus,
        residencyPositive = selectedResident && !mismatch,
        healthValue = healthValue,
        healthStatus = healthValue,
        healthPositive = healthPositive,
        processPss = processPss ?: "Unavailable",
        thermalStatus = thermalStatus ?: "Unavailable",
        latestRunValue = latestRunValue,
        latestRunStatus = latestRunStatus,
        latestRunPositive = latestMetrics != null && state.playground.phase == PlaygroundPhase.COMPLETED,
    )
}
