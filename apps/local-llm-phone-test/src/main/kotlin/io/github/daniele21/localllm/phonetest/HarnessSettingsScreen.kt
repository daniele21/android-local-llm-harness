@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessColors

@Composable
internal fun HarnessSettingsScreen(
    model: ImportedPhoneModel?,
    themePreference: HarnessThemePreference,
    onThemeChange: (HarnessThemePreference) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenBuild: () -> Unit,
    onOpenDeveloperTools: () -> Unit,
) {
    HarnessScreenList(title = null) {
        item { SettingsSectionLabel("Appearance") }
        item {
            HarnessCard {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose how Harness follows the device appearance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HarnessThemePreference.entries) { preference ->
                        FilterChip(
                            selected = themePreference == preference,
                            onClick = { onThemeChange(preference) },
                            label = { Text(preference.label) },
                        )
                    }
                }
            }
        }

        item { SettingsSectionLabel("Privacy") }
        item {
            SettingsRow(
                destination = HarnessDestination.DIAGNOSTICS,
                title = "Local inference & privacy",
                detail = "Prompts and generated output are not persisted by normal Harness telemetry.",
                trailing = "On-device",
                onClick = onOpenPrivacy,
            )
        }

        item { SettingsSectionLabel("Storage") }
        item {
            SettingsRow(
                destination = HarnessDestination.MODELS,
                title = "Selected model & local data",
                detail = "Inspect the selected model and protected local cleanup behavior.",
                trailing = model?.let { "${formatSettingsBytes(it.sizeBytes)} selected" } ?: "No selection",
                onClick = onOpenStorage,
            )
        }

        item { SettingsSectionLabel("About") }
        item {
            SettingsRow(
                destination = HarnessDestination.OVERVIEW,
                title = "Build & runtime info",
                detail = "Version, build identity and runtime metadata.",
                trailing = "›",
                onClick = onOpenBuild,
            )
        }

        item { SettingsSectionLabel("Advanced") }
        item {
            SettingsRow(
                destination = HarnessDestination.DIAGNOSTICS,
                title = "Developer tools",
                detail = "Health, logs, validation and advanced evidence surfaces.",
                trailing = "›",
                onClick = onOpenDeveloperTools,
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsRow(destination: HarnessDestination, title: String, detail: String, trailing: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Open $title",
                role = Role.Button,
                onClick = onClick,
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HarnessDestinationIcon(destination, selected = true, modifier = Modifier.padding(1.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = HarnessColors.Secondary)
        }
    }
}

private fun formatSettingsBytes(bytes: Long): String = "%.1f MB".format(java.util.Locale.ROOT, bytes / 1_048_576.0)
