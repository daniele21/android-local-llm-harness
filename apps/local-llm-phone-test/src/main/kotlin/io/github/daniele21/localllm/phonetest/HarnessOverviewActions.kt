package io.github.daniele21.localllm.phonetest

internal fun overviewPrimaryActionLabel(primaryAction: HarnessOverviewPrimaryAction): String = when (primaryAction) {
    HarnessOverviewPrimaryAction.CHOOSE_MODEL -> "Choose a model"
    HarnessOverviewPrimaryAction.RUN_PROMPT -> "Run a prompt"
    HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE -> "Resolve model state"
}

internal fun overviewPrimaryActionClick(
    primaryAction: HarnessOverviewPrimaryAction,
    onOpenPlayground: () -> Unit,
    onOpenModels: () -> Unit,
): () -> Unit = when (primaryAction) {
    HarnessOverviewPrimaryAction.CHOOSE_MODEL,
    HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE,
    -> onOpenModels

    HarnessOverviewPrimaryAction.RUN_PROMPT -> onOpenPlayground
}
