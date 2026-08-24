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

private data class OverviewModelState(
    val selectedModel: ImportedPhoneModel?,
    val selectedDigest: String?,
    val loadedDigest: String?,
    val mismatch: Boolean,
    val selectedResident: Boolean,
)

private data class OverviewHeroCopy(
    val label: String,
    val title: String,
    val detail: String,
)

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
        selectedModelValue = overviewSelectedModelValue(state, modelState),
        selectedModelStatus = overviewSelectedModelStatus(modelState),
        selectedModelPositive = modelState.selectedModel != null && !modelState.mismatch,
        runtimeValue = overviewRuntimeValue(diagnostics),
        residencyValue = overviewResidencyValue(modelState.loadedDigest),
        residencyStatus = overviewResidencyStatus(state, modelState),
        residencyPositive = modelState.selectedResident && !modelState.mismatch,
        healthValue = healthValue,
        healthStatus = healthValue,
        healthPositive = healthValue.equals("Pass", ignoreCase = true),
        processPss = processPss ?: "Unavailable",
        thermalStatus = thermalStatus ?: "Unavailable",
        latestRunValue = latestMetrics?.totalMs?.let { "$it ms total" } ?: "No runs yet",
        latestRunStatus = overviewLatestRunStatus(state),
        latestRunPositive = latestMetrics != null && state.playground.phase == PlaygroundPhase.COMPLETED,
    )
}

private fun overviewModelState(state: HarnessUiState): OverviewModelState {
    val selectedModel = state.importedModel
    val selectedDigest = selectedModel?.digest?.sha256
    val loadedDigest = state.modelInventory.loadedDigest
    val mismatch = state.modelInventory.degradedCount > 0 ||
        (selectedDigest != null && loadedDigest != null && selectedDigest != loadedDigest)
    return OverviewModelState(
        selectedModel = selectedModel,
        selectedDigest = selectedDigest,
        loadedDigest = loadedDigest,
        mismatch = mismatch,
        selectedResident = selectedDigest != null && selectedDigest == loadedDigest,
    )
}

private fun overviewPrimaryAction(modelState: OverviewModelState): HarnessOverviewPrimaryAction = when {
    modelState.selectedModel == null -> HarnessOverviewPrimaryAction.CHOOSE_MODEL
    modelState.mismatch -> HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE
    else -> HarnessOverviewPrimaryAction.RUN_PROMPT
}

private fun overviewHeroCopy(
    primaryAction: HarnessOverviewPrimaryAction,
    modelState: OverviewModelState,
): OverviewHeroCopy = OverviewHeroCopy(
    label = overviewHeroLabel(primaryAction),
    title = overviewHeroTitle(primaryAction, modelState.selectedModel),
    detail = overviewHeroDetail(primaryAction, modelState.selectedResident),
)

private fun overviewHeroLabel(primaryAction: HarnessOverviewPrimaryAction): String = when (primaryAction) {
    HarnessOverviewPrimaryAction.CHOOSE_MODEL -> "MODEL REQUIRED"
    HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE -> "MODEL STATE NEEDS ATTENTION"
    HarnessOverviewPrimaryAction.RUN_PROMPT -> "READY TO EVALUATE"
}

private fun overviewHeroTitle(
    primaryAction: HarnessOverviewPrimaryAction,
    selectedModel: ImportedPhoneModel?,
): String = when (primaryAction) {
    HarnessOverviewPrimaryAction.CHOOSE_MODEL -> "Choose a model to begin"
    HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE -> "Reconcile model state before the next run"
    HarnessOverviewPrimaryAction.RUN_PROMPT -> selectedModel?.fileName ?: "Ready to evaluate"
}

private fun overviewHeroDetail(
    primaryAction: HarnessOverviewPrimaryAction,
    selectedResident: Boolean,
): String = when {
    primaryAction == HarnessOverviewPrimaryAction.CHOOSE_MODEL ->
        "Choose a reviewed Qwen3.5 model for this device, then run a prompt with a measured configuration."

    primaryAction == HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE ->
        "The selected model and runtime residency do not agree. Review Models before running another inference."

    selectedResident ->
        "Selected and currently resident in memory. The next run can reuse the local runtime when policy allows."

    else ->
        "Selected on this device. The runtime will load it explicitly when the next inference requires it."
}

private fun overviewSelectedModelValue(
    state: HarnessUiState,
    modelState: OverviewModelState,
): String = state.modelInventory.selectedItem?.displayName ?: modelState.selectedModel?.fileName ?: "No model selected"

private fun overviewSelectedModelStatus(modelState: OverviewModelState): String = when {
    modelState.selectedModel == null -> "Not selected"
    modelState.mismatch -> "Needs attention"
    modelState.selectedResident -> "In memory"
    else -> "Selected"
}

private fun overviewRuntimeValue(diagnostics: DiagnosticsUiState): String =
    diagnostics.runtime?.state?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Unavailable"

private fun overviewResidencyValue(loadedDigest: String?): String =
    loadedDigest?.let { "${it.take(12)}…" } ?: "No model in memory"

private fun overviewResidencyStatus(
    state: HarnessUiState,
    modelState: OverviewModelState,
): String = when {
    state.playground.active -> "Running"
    modelState.mismatch -> "Mismatch"
    modelState.selectedResident -> "Resident"
    modelState.loadedDigest != null -> "Resident"
    else -> "Not resident"
}

private fun overviewLatestRunStatus(state: HarnessUiState): String = if (state.playground.metrics == null) {
    "Not run"
} else {
    state.playground.phase.name.lowercase().replaceFirstChar(Char::uppercase)
}
