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
    val resourceEvidenceAvailable: Boolean,
    val latestRunValue: String,
    val latestRunStatus: String,
    val latestRunPositive: Boolean,
)

private data class OverviewModelState(
    val selectedModel: ImportedPhoneModel?,
    val selectedDisplayName: String?,
    val loadedDigest: String?,
    val mismatch: Boolean,
    val selectedResident: Boolean,
)

private data class OverviewHeroCopy(val label: String, val title: String, val detail: String)

internal fun harnessOverviewPresentation(
    state: HarnessUiState,
    diagnostics: DiagnosticsUiState,
    processPss: String?,
    thermalStatus: String?,
): HarnessOverviewPresentation {
    val modelState = overviewModelState(state)
    val primaryAction = overviewPrimaryAction(modelState)
    val hero = overviewHeroCopy(primaryAction, modelState)
    val latestMetrics = state.playground.metrics
    val healthValue = diagnostics.healthStatus

    return HarnessOverviewPresentation(
        heroLabel = hero.label,
        heroTitle = hero.title,
        heroDetail = hero.detail,
        primaryAction = primaryAction,
        selectedModelValue = modelState.selectedDisplayName ?: "No model selected",
        selectedModelStatus = overviewSelectedModelStatus(modelState),
        selectedModelPositive = modelState.selectedModel != null && !modelState.mismatch,
        runtimeValue = diagnostics.runtime?.state?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Unavailable",
        residencyValue = modelState.loadedDigest?.let { "${it.take(12)}…" } ?: "No model in memory",
        residencyStatus = overviewResidencyStatus(state, modelState),
        residencyPositive = modelState.selectedResident && !modelState.mismatch,
        healthValue = healthValue,
        healthStatus = healthValue,
        healthPositive = healthValue.equals("Pass", ignoreCase = true),
        processPss = processPss ?: "Unavailable",
        thermalStatus = thermalStatus ?: "Unavailable",
        resourceEvidenceAvailable = processPss != null || thermalStatus != null,
        latestRunValue = latestMetrics?.totalMs?.let { "$it ms total" } ?: "No runs yet",
        latestRunStatus = overviewLatestRunStatus(state),
        latestRunPositive = latestMetrics != null && state.playground.phase == PlaygroundPhase.COMPLETED,
    )
}

private fun overviewModelState(state: HarnessUiState): OverviewModelState {
    val selectedModel = state.importedModel
    val selectedDigest = selectedModel?.digest?.sha256
    val loadedDigest = state.modelInventory.loadedDigest
    val selectedInventoryItem = state.modelInventory.selectedItem
    val selectedItemDegraded = selectedInventoryItem?.degradation != null
    val residencyConflict = selectedDigest != null && loadedDigest != null && selectedDigest != loadedDigest
    return OverviewModelState(
        selectedModel = selectedModel,
        selectedDisplayName = selectedInventoryItem?.displayName ?: selectedModel?.fileName,
        loadedDigest = loadedDigest,
        mismatch = selectedItemDegraded || residencyConflict,
        selectedResident = selectedDigest != null && selectedDigest == loadedDigest,
    )
}

private fun overviewPrimaryAction(modelState: OverviewModelState): HarnessOverviewPrimaryAction = when {
    modelState.selectedModel == null -> HarnessOverviewPrimaryAction.CHOOSE_MODEL
    modelState.mismatch -> HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE
    else -> HarnessOverviewPrimaryAction.RUN_PROMPT
}

private fun overviewHeroCopy(primaryAction: HarnessOverviewPrimaryAction, modelState: OverviewModelState): OverviewHeroCopy =
    OverviewHeroCopy(
        label = when (primaryAction) {
            HarnessOverviewPrimaryAction.CHOOSE_MODEL -> "MODEL REQUIRED"
            HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE -> "MODEL STATE NEEDS ATTENTION"
            HarnessOverviewPrimaryAction.RUN_PROMPT -> "READY TO EVALUATE"
        },
        title = when (primaryAction) {
            HarnessOverviewPrimaryAction.CHOOSE_MODEL -> "Choose a model to begin"
            HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE -> "Reconcile model state before the next run"
            HarnessOverviewPrimaryAction.RUN_PROMPT -> modelState.selectedDisplayName ?: "Ready to evaluate"
        },
        detail = overviewHeroDetail(primaryAction, modelState.selectedResident),
    )

private fun overviewHeroDetail(primaryAction: HarnessOverviewPrimaryAction, selectedResident: Boolean): String = when {
    primaryAction == HarnessOverviewPrimaryAction.CHOOSE_MODEL ->
        "Choose a reviewed Qwen3.5 model for this device, then run a prompt with a measured configuration."

    primaryAction == HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE ->
        "The selected model and runtime residency do not agree. Review Models before running another inference."

    selectedResident ->
        "Run a prompt for a quick measured check, or open Performance for repeatable evaluation before choosing a model or configuration."

    else ->
        "Selected on this device. Run a prompt for a quick measured check, or open Performance for repeatable evaluation; the runtime will load the model explicitly when needed."
}

private fun overviewSelectedModelStatus(modelState: OverviewModelState): String = when {
    modelState.selectedModel == null -> "Not selected"
    modelState.mismatch -> "Needs attention"
    modelState.selectedResident -> "In memory"
    else -> "Selected"
}

private fun overviewResidencyStatus(state: HarnessUiState, modelState: OverviewModelState): String = when {
    state.playground.active -> "Running"
    modelState.mismatch -> "Mismatch"
    modelState.selectedResident -> "Resident"
    modelState.loadedDigest != null -> "Other model resident"
    else -> "Not resident"
}

private fun overviewLatestRunStatus(state: HarnessUiState): String = if (state.playground.metrics == null) {
    "Not run"
} else {
    state.playground.phase.name.lowercase().replaceFirstChar(Char::uppercase)
}
