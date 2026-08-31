package io.github.daniele21.localllm.phonetest

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

internal fun NavGraphBuilder.installApplicationsListRoute(
    navController: NavHostController,
    state: HarnessApplicationsReadState,
    callbacks: HarnessApplicationsGraphCallbacks,
) {
    composable(HarnessDestination.APPS.route) {
        HarnessApplicationsScreen(
            state = state,
            onRefresh = callbacks.onRefresh,
            onCreateConnection = {
                callbacks.onClearMutationFeedback()
                navController.navigate(HarnessApplicationRoutes.newApplication())
            },
            onOpenApplication = { applicationId ->
                callbacks.onClearMutationFeedback()
                navController.navigate(HarnessApplicationRoutes.application(applicationId))
            },
        )
    }
}

internal fun NavGraphBuilder.installApplicationDetailRoute(
    navController: NavHostController,
    state: HarnessApplicationsReadState,
    mutationState: HarnessApplicationsMutationState,
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
            val selectedApplication = snapshot.application(applicationId)
            val onOpenAssignment: (String, String) -> Unit = { appId, useCaseId ->
                callbacks.onClearMutationFeedback()
                navController.navigate(HarnessApplicationRoutes.assignment(appId, useCaseId))
            }
            val onConnectionEnabledChanged: (Boolean) -> Unit = { enabled ->
                selectedApplication?.let { application ->
                    callbacks.onSetApplicationConnectionEnabled(application.applicationId, enabled)
                }
            }
            if (useHarnessApplicationsMasterDetail(currentHarnessAdaptivePolicy())) {
                HarnessApplicationsMasterDetailScreen(
                    snapshot = snapshot,
                    selectedApplication = selectedApplication,
                    mutationState = mutationState,
                    onRefresh = callbacks.onRefresh,
                    onCreateConnection = {
                        callbacks.onClearMutationFeedback()
                        navController.navigate(HarnessApplicationRoutes.newApplication())
                    },
                    onConnectionEnabledChanged = onConnectionEnabledChanged,
                    onDismissFeedback = callbacks.onClearMutationFeedback,
                    onOpenApplication = { selectedApplicationId ->
                        callbacks.onClearMutationFeedback()
                        navController.navigate(HarnessApplicationRoutes.application(selectedApplicationId)) {
                            popUpTo(HarnessDestination.APPS.route)
                            launchSingleTop = true
                        }
                    },
                    onOpenAssignment = onOpenAssignment,
                )
            } else {
                HarnessApplicationDetailScreen(
                    application = selectedApplication,
                    mutationState = mutationState,
                    onConnectionEnabledChanged = onConnectionEnabledChanged,
                    onReload = callbacks.onRefresh,
                    onDismissFeedback = callbacks.onClearMutationFeedback,
                    onOpenAssignment = onOpenAssignment,
                )
            }
        }
    }
}

internal fun NavGraphBuilder.installAssignmentRoute(
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
