@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun HarnessScreenList(
    title: String?,
    supportingText: String? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = harnessScreenHorizontalPadding(maxWidth)
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = harnessContentMaxWidth)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (title != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = MaterialTheme.typography.headlineLarge)
                        if (supportingText != null) {
                            Text(
                                supportingText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            content()
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

internal fun harnessScreenHorizontalPadding(width: Dp): Dp = when {
    width < 600.dp -> 16.dp
    width < 840.dp -> 24.dp
    else -> 32.dp
}

private val harnessContentMaxWidth = 960.dp
