package io.github.daniele21.localllm.phonetest

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

internal fun NavGraphBuilder.installHarnessApplicationsGraph(
    navController: NavHostController,
    state: HarnessApplicationsReadState,
    mutationState: HarnessApplicationsMutationState,
    onRefresh: () -> Unit,
    onSetDefaultPreset: (String, HarnessAssignmentSummary, HarnessPresetSummary) -> Unit,
    onCreateCustomPreset: (String, HarnessAssignmentSummary, HarnessPresetSummary, String, Boolean, Int?) -> Unit,
    onClearMutationFeedback: () -> Unit,
) {
    val callbacks = HarnessApplicationsGraphCallbacks(
        onRefresh = onRefresh,
        onSetDefaultPreset = onSetDefaultPreset,
        onCreateCustomPreset = onCreateCustomPreset,
        onClearMutationFeedback = onClearMutationFeedback,
    )
    installApplicationsListRoute(navController, state, callbacks)
    installApplicationDetailRoute(navController, state, callbacks)
    installAssignmentRoute(navController, state, callbacks)
    installPresetRoute(navController, state, mutationState, callbacks)
    installNewPresetRoute(navController, state, mutationState, callbacks)
    installTechnicalDetailsRoute(state, callbacks)
}
