package io.github.daniele21.localllm.phonetest

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.daniele21.localllm.audit.InferenceAuditFailureCode

internal fun NavGraphBuilder.installHarnessInferenceActivityGraph(navController: NavHostController) {
    composable(HarnessDestination.ACTIVITY.route) {
        val context = LocalContext.current
        val source = remember(context) { HarnessRuntimeGraph.from(context).inferenceActivitySource }
        val activityViewModel: HarnessInferenceActivityViewModel = viewModel()
        val state by activityViewModel.state.collectAsStateWithLifecycle()
        DisposableEffect(source) {
            activityViewModel.attach(source)
            onDispose { activityViewModel.detach(source) }
        }
        HarnessInferenceActivityScreen(
            state = state,
            onRefresh = activityViewModel::refresh,
            onOpenDetail = { requestId ->
                navController.navigate(HarnessInferenceActivityRoutes.detail(requestId))
            },
            onClearHistory = activityViewModel::clearTerminalHistory,
            onClearFeedback = activityViewModel::clearFeedback,
        )
    }
    composable(
        route = HarnessInferenceActivityRoutes.DETAIL_PATTERN,
        arguments = listOf(
            navArgument(HarnessInferenceActivityRoutes.REQUEST_ID_ARGUMENT) {
                type = NavType.StringType
            },
        ),
    ) { entry ->
        val context = LocalContext.current
        val source = remember(context) { HarnessRuntimeGraph.from(context).inferenceActivitySource }
        val activityViewModel: HarnessInferenceActivityViewModel = viewModel()
        val state by activityViewModel.state.collectAsStateWithLifecycle()
        val requestId = HarnessInferenceActivityRoutes.decodeRequestId(
            entry.arguments?.getString(HarnessInferenceActivityRoutes.REQUEST_ID_ARGUMENT),
        )
        DisposableEffect(source) {
            activityViewModel.attach(source)
            onDispose { activityViewModel.detach(source) }
        }
        LaunchedEffect(requestId) {
            requestId?.let(activityViewModel::openDetail)
        }
        if (requestId == null) {
            HarnessInferenceActivityDetailScreen(
                state = state.copy(
                    selectedRequestId = "invalid",
                    detailLoading = false,
                    detail = null,
                    detailErrorCode = InferenceAuditFailureCode.NOT_FOUND,
                ),
                requestId = "invalid",
                onRetry = {},
            )
        } else {
            HarnessInferenceActivityDetailScreen(
                state = state,
                requestId = requestId,
                onRetry = { activityViewModel.openDetail(requestId) },
            )
        }
    }
}
