package io.github.daniele21.localllm.phonetest

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument

internal fun HarnessApplicationsSnapshot.application(applicationId: String?): HarnessApplicationSummary? =
    applications.firstOrNull { it.applicationId == applicationId }

internal fun HarnessApplicationSummary.assignment(useCaseId: String?): HarnessAssignmentSummary? =
    assignments.firstOrNull { it.useCaseId == useCaseId }

internal fun HarnessAssignmentSummary.preset(presetId: String?, revision: Int?): HarnessPresetSummary? =
    availablePresets.firstOrNull { it.presetId == presetId && it.revision == revision }

internal fun applicationUseCaseArguments() = listOf(
    navArgument(HarnessApplicationRoutes.APPLICATION_ID_ARGUMENT) { type = NavType.StringType },
    navArgument(HarnessApplicationRoutes.USE_CASE_ID_ARGUMENT) { type = NavType.StringType },
)

internal fun presetArguments() = applicationUseCaseArguments() + listOf(
    navArgument(HarnessApplicationRoutes.PRESET_ID_ARGUMENT) { type = NavType.StringType },
    navArgument(HarnessApplicationRoutes.PRESET_REVISION_ARGUMENT) { type = NavType.IntType },
)

internal fun NavBackStackEntry.applicationIdentity(includePreset: Boolean = false): HarnessApplicationRouteIdentity? =
    HarnessApplicationRoutes.identity(
        encodedApplicationId = arguments?.getString(HarnessApplicationRoutes.APPLICATION_ID_ARGUMENT),
        encodedUseCaseId = arguments?.getString(HarnessApplicationRoutes.USE_CASE_ID_ARGUMENT),
        encodedPresetId = if (includePreset) arguments?.getString(HarnessApplicationRoutes.PRESET_ID_ARGUMENT) else null,
        presetRevision = if (includePreset) arguments?.getInt(HarnessApplicationRoutes.PRESET_REVISION_ARGUMENT) else null,
    )
