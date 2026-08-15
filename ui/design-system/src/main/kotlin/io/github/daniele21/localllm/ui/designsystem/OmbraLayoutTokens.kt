@file:Suppress("FunctionName", "LongParameterList")

package io.github.daniele21.localllm.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object OmbraLayoutTokens {
    val ReadableContentMaxWidth: Dp = 720.dp
    val DocumentPickerMinHeight: Dp = 160.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmbraTaskAppBar(
    title: String,
    modifier: Modifier = Modifier,
    stepLabel: String? = null,
    navigationLabel: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    require((navigationLabel == null) == (onNavigationClick == null)) {
        "navigationLabel and onNavigationClick must either both be set or both be null"
    }

    TopAppBar(
        modifier = modifier,
        title = {
            androidx.compose.foundation.layout.Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                stepLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            if (navigationLabel != null && onNavigationClick != null) {
                TextButton(
                    onClick = onNavigationClick,
                    modifier = Modifier.heightIn(min = LocalOmbraSpacing.current.minimumTouchTarget),
                ) {
                    Text(navigationLabel)
                }
            }
        },
        actions = actions,
    )
}

@Composable
fun OmbraScaffold(
    title: String,
    modifier: Modifier = Modifier,
    stepLabel: String? = null,
    navigationLabel: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            OmbraTaskAppBar(
                title = title,
                stepLabel = stepLabel,
                navigationLabel = navigationLabel,
                onNavigationClick = onNavigationClick,
                actions = topBarActions,
            )
        },
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier.widthIn(max = OmbraLayoutTokens.ReadableContentMaxWidth).fillMaxWidth(),
                content = { content(innerPadding) },
            )
        }
    }
}
