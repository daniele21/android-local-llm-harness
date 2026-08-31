@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessErrorState
import io.github.daniele21.localllm.ui.designsystem.HarnessLoadingState
import io.github.daniele21.localllm.ui.designsystem.HarnessRecoveryCard
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

private data class HarnessPresetRouteActions(
    val onSetDefaultPreset: (String, HarnessAssignmentSummary, HarnessPresetSummary) -> Unit,
    val onReload: () -> Unit,
    val onDismissFeedback: () -> Unit,
    val onOpenTechnicalDetails: () -> Unit,
)

internal fun NavGraphBuilder.installPresetRoute(
    navController: NavHostController,
    state: HarnessApplicationsReadState,
    mutationState: HarnessApplicationsMutationState,
    callbacks: HarnessApplicationsGraphCallbacks,
) {
    composable(
        route = HarnessApplicationRoutes.PRESET_PATTERN,
        arguments = presetArguments(),
    ) { entry ->
        val identity = entry.applicationIdentity(includePreset = true)
        LaunchedEffect(identity) { callbacks.onClearMutationFeedback() }
        HarnessApplicationsRouteContent(state = state, onRefresh = callbacks.onRefresh) { snapshot ->
            val application = snapshot.application(identity?.applicationId)
            val assignment = application?.assignment(identity?.useCaseId)
            val preset = assignment?.preset(identity?.presetId, identity?.presetRevision)
            HarnessPresetRouteScreen(
                application = application,
                assignment = assignment,
                preset = preset,
                mutationState = mutationState,
                actions = HarnessPresetRouteActions(
                    onSetDefaultPreset = callbacks.onSetDefaultPreset,
                    onReload = callbacks.onRefresh,
                    onDismissFeedback = callbacks.onClearMutationFeedback,
                    onOpenTechnicalDetails = {
                        if (application != null && assignment != null && preset != null) {
                            navController.navigate(
                                HarnessApplicationRoutes.technicalDetails(
                                    application.applicationId,
                                    assignment.useCaseId,
                                    preset.presetId,
                                    preset.revision,
                                ),
                            )
                        }
                    },
                ),
            )
        }
    }
}

internal fun NavGraphBuilder.installNewPresetRoute(
    navController: NavHostController,
    state: HarnessApplicationsReadState,
    mutationState: HarnessApplicationsMutationState,
    callbacks: HarnessApplicationsGraphCallbacks,
) {
    composable(
        route = HarnessApplicationRoutes.NEW_PRESET_PATTERN,
        arguments = applicationUseCaseArguments(),
    ) { entry ->
        val identity = entry.applicationIdentity()
        LaunchedEffect(identity) { callbacks.onClearMutationFeedback() }
        HarnessApplicationsRouteContent(state = state, onRefresh = callbacks.onRefresh) { snapshot ->
            val application = snapshot.application(identity?.applicationId)
            val assignment = application?.assignment(identity?.useCaseId)
            HarnessCreatePresetScreen(
                application = application,
                assignment = assignment,
                mutationState = mutationState,
                actions = createPresetActions(navController, application, assignment, callbacks),
            )
        }
    }
}

private fun createPresetActions(
    navController: NavHostController,
    application: HarnessApplicationSummary?,
    assignment: HarnessAssignmentSummary?,
    callbacks: HarnessApplicationsGraphCallbacks,
) = HarnessCreatePresetActions(
    onSave = { basePreset, displayName, modelProfileId, contextTokens, generationOverrides ->
        if (application != null && assignment != null) {
            callbacks.onCreateCustomPreset(
                application.applicationId,
                assignment,
                basePreset,
                displayName,
                modelProfileId,
                contextTokens,
                generationOverrides,
            )
        }
    },
    onReload = callbacks.onRefresh,
    onClearFeedback = callbacks.onClearMutationFeedback,
    onViewSavedPreset = { presetId, presetRevision ->
        if (application != null && assignment != null) {
            navController.navigate(
                HarnessApplicationRoutes.preset(
                    application.applicationId,
                    assignment.useCaseId,
                    presetId,
                    presetRevision,
                ),
            )
        }
    },
    onDone = { navController.popBackStack() },
)

internal fun NavGraphBuilder.installTechnicalDetailsRoute(
    state: HarnessApplicationsReadState,
    callbacks: HarnessApplicationsGraphCallbacks,
) {
    composable(
        route = HarnessApplicationRoutes.TECHNICAL_DETAILS_PATTERN,
        arguments = presetArguments(),
    ) { entry ->
        val identity = entry.applicationIdentity(includePreset = true)
        HarnessApplicationsRouteContent(state = state, onRefresh = callbacks.onRefresh) { snapshot ->
            val application = snapshot.application(identity?.applicationId)
            val assignment = application?.assignment(identity?.useCaseId)
            HarnessApplicationTechnicalDetailsScreen(
                application = application,
                assignment = assignment,
                preset = assignment?.preset(identity?.presetId, identity?.presetRevision),
            )
        }
    }
}

@Composable
internal fun HarnessApplicationsRouteContent(
    state: HarnessApplicationsReadState,
    onRefresh: () -> Unit,
    content: @Composable (HarnessApplicationsSnapshot) -> Unit,
) {
    when (state) {
        HarnessApplicationsReadState.Loading -> HarnessLoadingState(
            title = "Loading configuration",
            detail = "Reading the current Harness control-plane state",
        )

        is HarnessApplicationsReadState.Failed -> HarnessRecoveryCard(
            title = "Configuration unavailable",
            detail = state.message,
            actionLabel = "Retry",
            onAction = onRefresh,
            modifier = Modifier.padding(LocalHarnessSpacing.current.large),
            tone = HarnessStatusTone.ERROR,
        )

        is HarnessApplicationsReadState.Loaded -> content(state.snapshot)
    }
}

@Composable
private fun HarnessPresetRouteScreen(
    application: HarnessApplicationSummary?,
    assignment: HarnessAssignmentSummary?,
    preset: HarnessPresetSummary?,
    mutationState: HarnessApplicationsMutationState,
    actions: HarnessPresetRouteActions,
) {
    if (application == null || assignment == null || preset == null) {
        HarnessErrorState(
            title = "Preset unavailable",
            detail = "The preset identity may have changed. Return to the assigned use case and reload.",
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.small),
    ) {
        HarnessPresetMutationFeedback(
            state = mutationState,
            onReload = actions.onReload,
            onDismiss = actions.onDismissFeedback,
            modifier = Modifier.padding(horizontal = LocalHarnessSpacing.current.large),
        )
        HarnessPresetDetailScreen(
            preset = preset,
            onUseAsDefault = if (
                !preset.isDefault &&
                assignment.status == HarnessAssignmentStatus.ACTIVE &&
                mutationState != HarnessApplicationsMutationState.Saving
            ) {
                { actions.onSetDefaultPreset(application.applicationId, assignment, preset) }
            } else {
                null
            },
            onOpenTechnicalDetails = actions.onOpenTechnicalDetails,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HarnessPresetMutationFeedback(
    state: HarnessApplicationsMutationState,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        HarnessApplicationsMutationState.Idle -> Unit

        HarnessApplicationsMutationState.Saving -> HarnessCard(modifier = modifier) {
            HarnessStatusBadge("Saving", HarnessStatusTone.INFO)
            Text("Updating the default preset in the control plane.", style = MaterialTheme.typography.bodyMedium)
        }

        is HarnessApplicationsMutationState.Saved -> HarnessCard(modifier = modifier, emphasized = true) {
            HarnessStatusBadge("Saved", HarnessStatusTone.SUCCESS)
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            HarnessSecondaryButton(text = "Dismiss", onClick = onDismiss)
        }

        is HarnessApplicationsMutationState.Conflict -> HarnessRecoveryCard(
            title = "Configuration changed",
            detail = state.message,
            actionLabel = "Reload changes",
            onAction = onReload,
            modifier = modifier,
            tone = HarnessStatusTone.WARNING,
        )

        is HarnessApplicationsMutationState.Failed -> HarnessRecoveryCard(
            title = "Update unavailable",
            detail = state.message,
            actionLabel = "Reload state",
            onAction = onReload,
            modifier = modifier,
            tone = HarnessStatusTone.ERROR,
        )
    }
}
