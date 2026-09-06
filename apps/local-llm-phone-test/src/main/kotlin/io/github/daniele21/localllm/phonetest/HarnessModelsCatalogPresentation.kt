package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import java.util.Locale

internal fun loadingStableId(state: HarnessUiState, feedback: ModelActionFeedbackState): String? {
    if (!state.controllerBusy) return null
    return state.modelDistribution.models
        .firstOrNull { feedback.latest == "Loading ${it.fileName} into memory" }
        ?.stableId
}

internal fun ModelsAvailabilityFilter.matches(item: HarnessModelInventoryItem): Boolean = when (this) {
    ModelsAvailabilityFilter.ALL -> true
    ModelsAvailabilityFilter.INSTALLED -> item.installed
    ModelsAvailabilityFilter.AVAILABLE -> !item.installed
}

internal fun ModelsSizeFilter.matches(item: HarnessModelInventoryItem): Boolean = when (this) {
    ModelsSizeFilter.ALL -> true
    ModelsSizeFilter.B08 -> item.stableId.startsWith("qwen35-08b-")
    ModelsSizeFilter.B2 -> item.stableId.startsWith("qwen35-2b-")
    ModelsSizeFilter.B4 -> item.stableId.startsWith("qwen35-4b-")
}

internal fun orderGroupItems(group: ModelsSizeFilter, items: List<HarnessModelInventoryItem>): List<HarnessModelInventoryItem> =
    items.sortedBy { item ->
        if (item.stableId == group.suggestedModelId) 0 else 1
    }

internal fun modelsEmptyStateDetail(
    availabilityFilter: ModelsAvailabilityFilter,
    sizeFilter: ModelsSizeFilter,
    activeModelPresent: Boolean,
): String {
    val prefix = if (activeModelPresent) "No other" else "No"
    val size = sizeFilter.takeIf { it != ModelsSizeFilter.ALL }?.label?.let { " $it" }.orEmpty()
    return when (availabilityFilter) {
        ModelsAvailabilityFilter.INSTALLED -> "$prefix$size installed models match this filter."
        ModelsAvailabilityFilter.AVAILABLE -> "$prefix$size not-installed models match this filter."
        ModelsAvailabilityFilter.ALL -> "$prefix$size models match this filter."
    }
}

internal fun HarnessModelLifecycle.statusTone(): HarnessStatusTone = when (this) {
    HarnessModelLifecycle.LOADED -> HarnessStatusTone.SUCCESS

    HarnessModelLifecycle.SELECTED,
    HarnessModelLifecycle.DOWNLOADING,
    HarnessModelLifecycle.INSTALLING,
    HarnessModelLifecycle.VERIFIED_READY_TO_INSTALL,
    -> HarnessStatusTone.INFO

    HarnessModelLifecycle.DEGRADED,
    HarnessModelLifecycle.INCOMPATIBLE,
    -> HarnessStatusTone.WARNING

    HarnessModelLifecycle.FAILED -> HarnessStatusTone.ERROR

    HarnessModelLifecycle.INSTALLED,
    HarnessModelLifecycle.READY_TO_DOWNLOAD,
    HarnessModelLifecycle.CANCELLED,
    -> HarnessStatusTone.NEUTRAL
}

internal fun formatModelBytes(bytes: Long): String {
    if (bytes < 1_024L) return "$bytes B"
    val kib = bytes / 1_024.0
    if (kib < 1_024.0) return "%.1f KiB".format(Locale.US, kib)
    val mib = kib / 1_024.0
    if (mib < 1_024.0) return "%.1f MiB".format(Locale.US, mib)
    return "%.2f GiB".format(Locale.US, mib / 1_024.0)
}
