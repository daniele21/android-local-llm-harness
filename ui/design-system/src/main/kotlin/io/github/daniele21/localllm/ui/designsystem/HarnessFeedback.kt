@file:Suppress("FunctionName")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HarnessTopAppBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = HarnessAppBarMinimumHeight)
                .padding(horizontal = LocalHarnessSpacing.current.large),
            horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            actions()
        }
    }
}

@Composable
fun HarnessLoadingState(title: String, detail: String? = null, modifier: Modifier = Modifier) {
    HarnessFeedbackState(
        title = title,
        detail = detail,
        modifier = modifier,
        indicator = { CircularProgressIndicator() },
    )
}

@Composable
fun HarnessEmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    HarnessFeedbackState(title = title, detail = detail, modifier = modifier)
}

@Composable
fun HarnessErrorState(title: String, detail: String, modifier: Modifier = Modifier) {
    HarnessFeedbackState(
        title = title,
        detail = detail,
        modifier = modifier,
        titleColor = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun HarnessFeedbackState(
    title: String,
    detail: String?,
    modifier: Modifier,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    indicator: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(LocalHarnessSpacing.current.xLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
    ) {
        indicator?.invoke()
        Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val HarnessAppBarMinimumHeight = 64.dp
