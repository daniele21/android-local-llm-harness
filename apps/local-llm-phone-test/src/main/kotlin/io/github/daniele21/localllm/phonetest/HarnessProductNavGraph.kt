package io.github.daniele21.localllm.phonetest

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import io.github.daniele21.localllm.models.PresetGenerationOverrides

/**
 * App-level product graph composition used by the phone Harness shell.
 *
 * The historical function name is retained at the MainActivity boundary while feature route
 * ownership stays separated between Applications and inference Activity.
 */
internal fun NavGraphBuilder.installHarnessApplicationsGraph(
    navController: NavHostController,
    state: HarnessApplicationsReadState,
    mutationState: HarnessApplicationsMutationState,
    onRefresh: () -> Unit,
    onSetDefaultPreset: (String, HarnessAssignmentSummary, HarnessPresetSummary) -> Unit,
    onCreateCustomPreset: (
        String,
        HarnessAssignmentSummary,
        HarnessPresetSummary,
        String,
        String?,
        Int?,
        PresetGenerationOverrides?,
    ) -> Unit,
    onClearMutationFeedback: () -> Unit,
) {
    installHarnessApplicationsFeatureGraph(
        navController = navController,
        state = state,
        mutationState = mutationState,
        onRefresh = onRefresh,
        onSetDefaultPreset = onSetDefaultPreset,
        onCreateCustomPreset = onCreateCustomPreset,
        onClearMutationFeedback = onClearMutationFeedback,
    )
    installHarnessInferenceActivityGraph(navController)
}
