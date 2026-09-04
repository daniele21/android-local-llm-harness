@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessMinimumTouchTarget
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

@Composable
internal fun HarnessConnectionControlCard(
    application: HarnessApplicationSummary,
    saving: Boolean,
    onConnectionEnabledChanged: (Boolean) -> Unit,
) {
    val toggleSupported =
        application.status == HarnessApplicationStatus.AUTHORIZED ||
            application.status == HarnessApplicationStatus.DISABLED
    val enabled = application.status == HarnessApplicationStatus.AUTHORIZED
    HarnessCard(modifier = Modifier.testTag("application-connection-control")) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = HarnessMinimumTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall),
            ) {
                Text("Allow app connection", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled) {
                        "This app can authenticate to the shared runtime for its assigned use cases."
                    } else {
                        "Access is blocked at the Binder authorization boundary. Configuration is retained."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!toggleSupported) {
                    Text(
                        "Resolve the application identity state before changing access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onConnectionEnabledChanged,
                enabled = toggleSupported && !saving,
                modifier = Modifier.testTag("application-connection-enabled"),
            )
        }
    }
}
