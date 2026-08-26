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
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessErrorState
import io.github.daniele21.localllm.ui.designsystem.HarnessLoadingState
import io.github.daniele21.localllm.ui.designsystem.HarnessRecoveryCard
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

private data class HarnessApplicationsGraphCallbacks(
    val onRefresh: () -> Unit,
    val onSetDefaultPreset: (String, HarnessAssignmentSummary, HarnessPresetSummary) -> Unit,
    val onCreateCustomPreset: (String, HarnessAssignmentSummary, HarnessPresetSummary, String, Boolean, Int?) -> Unit,
    val onClearMutationFeedback: () -> Unit,
)

private data class HarnessPresetRouteActions(
    val onSetDefaultPreset: (String, HarnessAssignmentSummary, HarnessPresetSummary) -> Unit,
    val onReload: () -> Unit,
    val onDismissFeedback: () -> Unit,
    val onOpenTechnicalDetails: () -> Unit,
)

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

private fun NavGraphBuilder.installApplicationsListRoute(
    navController: NavHostController,
    state: HarnessApplicationsReadState,
    callbacks: HarnessApplicationsGraphCallbacks,
) {
    composable(HarnessDestination.APPS.route) {
        HarnessApplicationsScreen(
            state = state,
            onRefresh = callbacks.onRefresh,
            onOpenApplication = { applicationId ->
                navController.navigate(HarnessApplicationRoutes.application(applicationId))
            },
        )
    }
}

private fun NavGraphBuilder.installApplicationDetailRoute(
    navController: NavHostController,
    state: HarnessApplicationsReadState,
    callbacks: HarnessApplicationsGraphCallbacks,
) {
    composable(
        route = HarnessApplicationRoutes.APPLICATION_PATTERN,
        arguments = listOf(
            navArgument(HarnessApplicationRoutes.APPLICATION_ID_ARGUMENT) { type = NavType.StringType },
        ),
    ) { entry ->
        val applicationId = HarnessApplicationRoutes.decodeApplicationId(
            entry.arguments?.getString(HarnessApplicationRoutes.APPLICATION_ID_ARGUMENT),
        )
        HarnessApplicationsRouteContent(state = state, onRefresh = callbacks.onRefresh) { snapshot ->
            HarnessApplicationDetailScreen(
                application = snapshot.application(applicationId),
                onOpenAssignment = { appId, useCaseId ->
                    navController.navigate(HarnessApplicationRoutes.assignment(appId, useCaseId))
                },
            )
        }
    }
}

private fun NavGraphBuilder.installAssignmentRoute(
    navController: NavHostController,
    state: HarnessApplicationsReadState,
    callbacks: HarnessApplicationsGraphCallbacks,
) {
    composable(
        route = HarnessApplicationRoutes.ASSIGNMENT_PATTERN,
        arguments = applicationUseCaseArguments(),
    ) { entry ->
        val identity = entry.applicationIdentity()
        HarnessApplicationsRouteContent(state = state, onRefresh = callbacks.onRefresh) { snapshot ->
            val application = snapshot.application(identity?.applicationId)
            val assignment = application?.assignment(identity?.useCaseId)
            HarnessAssignedUseCaseScreen(
                applicationName = application?.displayName ?: "Application unavailable",
                assignment = assignment,
                onOpenPreset = { preset ->
                    if (application != null && assignment != null) {
                        navController.navigate(
                            HarnessApplicationRoutes.preset(
                                application.applicationId,
                                assignment.useCaseId,
                                preset.presetId,
                                preset.revision,
                            ),
                        )
                    }
                },
                onCreatePreset = createPresetNavigationAction(
                    navController = navController,
                    application = application,
                    assignment = assignment,
                    callbacks = callbacks,
                ),
            )
        }
    }
}

private fun createPresetNavigationAction(
    navController: NavHostController,
    application: HarnessApplicationSummary?,
    assignment: HarnessAssignmentSummary?,
    callbacks: HarnessApplicationsGraphCallbacks,
): (() -> Unit)? {
    if (
        application == null ||
        assignment?.status != HarnessAssignmentStatus.ACTIVE ||
        assignment.availablePresets.isEmpty()
    ) {
        return null
    }
    return {
        callbacks.onClearMutationFeedback()
        navController.navigate(
            HarnessApplicationRoutes.newPreset(application.applicationId, assignment.useCaseId),
        )
    }
}

private fun NavGraphBuilder.installPresetRoute(
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

private fun NavGraphBuilder.installNewPresetRoute(
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
    onSave = { basePreset, displayName, automaticModelSelection, contextTokens ->
        if (application != null && assignment != null) {
            callbacks.onCreateCustomPreset(
                application.applicationId,
                assignment,
                basePreset,
                displayName,
                automaticModelSelection,
                contextTokens,
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

private fun NavGraphBuilder.installTechnicalDetailsRoute(
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
private fun HarnessApplicationsRouteContent(
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

private fun HarnessApplicationsSnapshot.application(applicationId: String?): HarnessApplicationSummary? =
    applications.firstOrNull { it.applicationId == applicationId }

private fun HarnessApplicationSummary.assignment(useCaseId: String?): HarnessAssignmentSummary? =
    assignments.firstOrNull { it.useCaseId == useCaseId }

private fun HarnessAssignmentSummary.preset(presetId: String?, revision: Int?): HarnessPresetSummary? =
    availablePresets.firstOrNull { it.presetId == presetId && it.revision == revision }

private fun applicationUseCaseArguments() = listOf(
    navArgument(HarnessApplicationRoutes.APPLICATION_ID_ARGUMENT) { type = NavType.StringType },
    navArgument(HarnessApplicationRoutes.USE_CASE_ID_ARGUMENT) { type = NavType.StringType },
)

private fun presetArguments() = applicationUseCaseArguments() + listOf(
    navArgument(HarnessApplicationRoutes.PRESET_ID_ARGUMENT) { type = NavType.StringType },
    navArgument(HarnessApplicationRoutes.PRESET_REVISION_ARGUMENT) { type = NavType.IntType },
)

private fun androidx.navigation.NavBackStackEntry.applicationIdentity(includePreset: Boolean = false): HarnessApplicationRouteIdentity? =
    HarnessApplicationRoutes.identity(
        encodedApplicationId = arguments?.getString(HarnessApplicationRoutes.APPLICATION_ID_ARGUMENT),
        encodedUseCaseId = arguments?.getString(HarnessApplicationRoutes.USE_CASE_ID_ARGUMENT),
        encodedPresetId = if (includePreset) arguments?.getString(HarnessApplicationRoutes.PRESET_ID_ARGUMENT) else null,
        presetRevision = if (includePreset) arguments?.getInt(HarnessApplicationRoutes.PRESET_REVISION_ARGUMENT) else null,
    )
