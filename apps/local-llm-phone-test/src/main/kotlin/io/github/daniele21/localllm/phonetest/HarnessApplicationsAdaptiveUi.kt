@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal fun useHarnessApplicationsMasterDetail(policy: HarnessAdaptivePolicy): Boolean =
    policy.useNavigationRail && !policy.stackDenseContent

@Composable
internal fun HarnessApplicationsMasterDetailScreen(
    snapshot: HarnessApplicationsSnapshot,
    selectedApplication: HarnessApplicationSummary?,
    onRefresh: () -> Unit,
    onOpenApplication: (String) -> Unit,
    onOpenAssignment: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize().testTag("applications-master-detail")) {
        Box(
            modifier = Modifier
                .widthIn(min = MASTER_LIST_MIN_WIDTH, max = MASTER_LIST_MAX_WIDTH)
                .fillMaxHeight(),
        ) {
            HarnessApplicationsScreen(
                state = HarnessApplicationsReadState.Loaded(snapshot),
                onRefresh = onRefresh,
                onOpenApplication = onOpenApplication,
                modifier = Modifier.fillMaxSize(),
            )
        }
        VerticalDivider(modifier = Modifier.fillMaxHeight())
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            HarnessApplicationDetailScreen(
                application = selectedApplication,
                onOpenAssignment = onOpenAssignment,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val MASTER_LIST_MIN_WIDTH = 280.dp
private val MASTER_LIST_MAX_WIDTH = 400.dp
