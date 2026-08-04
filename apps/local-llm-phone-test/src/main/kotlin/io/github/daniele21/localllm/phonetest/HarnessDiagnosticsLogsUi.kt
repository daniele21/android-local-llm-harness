@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.observability.LogLevel
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessMetric
import io.github.daniele21.localllm.ui.designsystem.HarnessMetricRow
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun LazyListScope.logDiagnostics(
    state: DiagnosticsLogUiState,
    filter: DiagnosticsLogFilter,
    timeline: DiagnosticsRequestTimelineUi?,
    onFilterChange: (DiagnosticsLogFilter) -> Unit,
    onOpenTimeline: (String) -> Unit,
    onCloseTimeline: () -> Unit,
    onCopyLog: (DiagnosticsLogUi) -> Unit,
) {
    item {
        HarnessCard {
            Text("Structured logs", style = MaterialTheme.typography.titleLarge)
            when {
                state.sourceError != null -> Text(state.sourceError)
                state.totalCount == 0 -> Text("No structured logs have been recorded in this process.")
                state.logs.isEmpty() && state.filterActive -> Text("No logs match the active filters.")
                else -> Text("Showing ${state.logs.size} of ${state.totalCount} bounded privacy-safe log entries.")
            }
            LogLevelFilters(filter, onFilterChange)
            OutlinedTextField(
                value = filter.componentQuery,
                onValueChange = { onFilterChange(filter.copy(componentQuery = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Component filter") },
                singleLine = true,
            )
            OutlinedTextField(
                value = filter.eventQuery,
                onValueChange = { onFilterChange(filter.copy(eventQuery = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Event filter") },
                singleLine = true,
            )
            OutlinedTextField(
                value = filter.requestQuery,
                onValueChange = { onFilterChange(filter.copy(requestQuery = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Request filter") },
                singleLine = true,
            )
            OutlinedTextField(
                value = filter.searchQuery,
                onValueChange = { onFilterChange(filter.copy(searchQuery = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search safe fields") },
                singleLine = true,
            )
            HarnessSecondaryButton(
                "Clear log filters",
                enabled = filter.active,
                onClick = { onFilterChange(DiagnosticsLogFilter()) },
            )
        }
    }

    timeline?.let { requestTimeline ->
        item(key = "timeline-summary:${requestTimeline.requestId}") {
            HarnessCard {
                Text("Request timeline", style = MaterialTheme.typography.titleLarge)
                HarnessMetricRow {
                    HarnessMetric("Request", requestTimeline.requestIdPrefix, Modifier.weight(1f))
                    HarnessMetric("Run status", requestTimeline.runStatus, Modifier.weight(1f))
                }
                when {
                    requestTimeline.sourceError != null -> Text(requestTimeline.sourceError)
                    requestTimeline.events.isEmpty() -> Text("No correlated structured events were recorded for this request.")
                    else -> Text("${requestTimeline.events.size} events ordered chronologically.")
                }
                HarnessSecondaryButton("Close timeline", onClick = onCloseTimeline)
            }
        }
        items(
            items = requestTimeline.events,
            key = { "timeline:${it.stableId}" },
        ) { log ->
            LogCard(log, timeline = true, onOpenTimeline = onOpenTimeline, onCopyLog = onCopyLog)
        }
    }

    items(
        items = state.logs,
        key = { "log:${it.stableId}" },
    ) { log ->
        LogCard(log, timeline = false, onOpenTimeline = onOpenTimeline, onCopyLog = onCopyLog)
    }
}

@androidx.compose.runtime.Composable
private fun LogLevelFilters(filter: DiagnosticsLogFilter, onFilterChange: (DiagnosticsLogFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = filter.level == null,
                onClick = { onFilterChange(filter.copy(level = null)) },
                label = { Text("All") },
            )
        }
        items(LogLevel.entries) { level ->
            FilterChip(
                selected = filter.level == level,
                onClick = { onFilterChange(filter.copy(level = level)) },
                label = { Text(level.name) },
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun LogCard(log: DiagnosticsLogUi, timeline: Boolean, onOpenTimeline: (String) -> Unit, onCopyLog: (DiagnosticsLogUi) -> Unit) {
    HarnessCard {
        Text("${log.level} · ${log.event}", style = MaterialTheme.typography.titleMedium)
        HarnessMetricRow {
            HarnessMetric("Component", log.component, Modifier.weight(1f))
            HarnessMetric("Request", log.requestIdPrefix, Modifier.weight(1f))
        }
        HarnessMetricRow {
            HarnessMetric("Time", formatTimestamp(log.timestampEpochMs), Modifier.weight(1f))
            HarnessMetric(
                "Offset",
                log.offsetMs?.let { if (it >= 0) "+$it ms" else "$it ms" } ?: "Unavailable",
                Modifier.weight(1f),
            )
        }
        SelectionContainer {
            Text(
                log.fields.joinToString(separator = "\n") { "${it.name}=${it.value}" }.ifEmpty { "No safe fields" },
                fontFamily = FontFamily.Monospace,
            )
        }
        if (!timeline && log.requestId != null) {
            HarnessSecondaryButton("View request timeline") {
                onOpenTimeline(log.requestId)
            }
        }
        HarnessSecondaryButton("Copy log entry") { onCopyLog(log) }
    }
}

internal fun DiagnosticsLogUi.copyText(): String = buildString {
    append(level)
    append(" · ")
    append(event)
    append('\n')
    append("Time: ")
    append(formatTimestamp(timestampEpochMs))
    append('\n')
    append("Component: ")
    append(component)
    append('\n')
    append("Request: ")
    append(requestIdPrefix)
    fields.forEach { field ->
        append('\n')
        append(field.name)
        append('=')
        append(field.value)
    }
}

private fun formatTimestamp(epochMs: Long): String = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMs))

private val TIMESTAMP_FORMATTER = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
    .withZone(ZoneId.systemDefault())
