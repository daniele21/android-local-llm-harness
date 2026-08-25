@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone

internal fun modelActionStatusTone(tone: ModelActionFeedbackTone): HarnessStatusTone = when (tone) {
    ModelActionFeedbackTone.INFO -> HarnessStatusTone.INFO
    ModelActionFeedbackTone.SUCCESS -> HarnessStatusTone.SUCCESS
    ModelActionFeedbackTone.ERROR -> HarnessStatusTone.ERROR
}

@Composable
internal fun ModelActionStatus(feedback: ModelActionFeedbackState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessStatusBadge(
            label = when (feedback.tone) {
                ModelActionFeedbackTone.INFO -> "STATUS"
                ModelActionFeedbackTone.SUCCESS -> "OK"
                ModelActionFeedbackTone.ERROR -> "ERROR"
            },
            tone = modelActionStatusTone(feedback.tone),
        )
        Text(
            text = feedback.latest,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (feedback.tone == ModelActionFeedbackTone.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
