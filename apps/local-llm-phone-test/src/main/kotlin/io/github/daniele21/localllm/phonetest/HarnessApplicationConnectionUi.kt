@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessEmptyState
import io.github.daniele21.localllm.ui.designsystem.HarnessKeyValueRow
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessRecoveryCard
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

internal fun NavGraphBuilder.installNewApplicationConnectionRoute(
    navController: NavHostController,
    state: HarnessApplicationsReadState,
    mutationState: HarnessApplicationsMutationState,
    callbacks: HarnessApplicationsGraphCallbacks,
) {
    composable(HarnessApplicationRoutes.NEW_APPLICATION_ROUTE) {
        val applicationsViewModel = activityApplicationsViewModel()
        LaunchedEffect(Unit) { callbacks.onClearMutationFeedback() }
        HarnessApplicationsRouteContent(state = state, onRefresh = callbacks.onRefresh) { snapshot ->
            HarnessCreateApplicationConnectionScreen(
                options = snapshot.connectionOptions,
                mutationState = mutationState,
                onCreate = applicationsViewModel::createApplicationConnection,
                onReload = callbacks.onRefresh,
                onClearFeedback = callbacks.onClearMutationFeedback,
                onDone = { navController.popBackStack() },
            )
        }
    }
}

@Composable
internal fun activityApplicationsViewModel(): HarnessApplicationsReadViewModel {
    val owner = LocalContext.current as? ComponentActivity
        ?: error("Application control-plane routes require a ComponentActivity owner")
    return viewModel(viewModelStoreOwner = owner)
}

@Composable
internal fun HarnessCreateApplicationConnectionScreen(
    options: List<HarnessConnectionUseCaseOption>,
    mutationState: HarnessApplicationsMutationState,
    onCreate: (String, String, String, String, String, String, Int) -> Unit,
    onReload: () -> Unit,
    onClearFeedback: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) {
        HarnessEmptyState(
            title = "No connectable use cases",
            detail = "A published use case with at least one available preset is required before an app connection can be created.",
            modifier = modifier,
        )
        return
    }

    var displayName by rememberSaveable { mutableStateOf("") }
    var applicationId by rememberSaveable { mutableStateOf("") }
    var packageName by rememberSaveable { mutableStateOf("") }
    var signerSha256 by rememberSaveable { mutableStateOf("") }
    var selectedUseCaseId by rememberSaveable { mutableStateOf(options.first().useCaseId) }
    var selectedPresetKey by rememberSaveable {
        mutableStateOf(options.first().presets.first().identityKey())
    }

    val selectedUseCase = options.firstOrNull { it.useCaseId == selectedUseCaseId } ?: options.first()
    val selectedPreset = selectedUseCase.presets.firstOrNull { it.identityKey() == selectedPresetKey }
        ?: selectedUseCase.presets.first()
    val saving = mutationState == HarnessApplicationsMutationState.Saving
    val saved = mutationState is HarnessApplicationsMutationState.Saved
    val signerValid = signerSha256.length == SHA256_FINGERPRINT_LENGTH && signerSha256.all(Char::isHexDigit)
    val formValid = displayName.isNotBlank() && applicationId.isNotBlank() && packageName.isNotBlank() && signerValid

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("create-application-connection"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(LocalHarnessSpacing.current.large),
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall)) {
                Text("New app connection", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Authorize one exact Android package and signing identity to use a selected Harness use case.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            HarnessCard {
                Text("Application identity", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Package and signer are checked at the Binder security boundary. They must match the installed consumer app exactly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConnectionTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        onClearFeedback()
                    },
                    label = "App name",
                    supportingText = "Human-readable name shown in Harness",
                    enabled = !saving && !saved,
                    testTag = "connection-display-name",
                )
                ConnectionTextField(
                    value = applicationId,
                    onValueChange = {
                        applicationId = it
                        onClearFeedback()
                    },
                    label = "Application ID",
                    supportingText = "Stable Harness identity for this consumer app",
                    enabled = !saving && !saved,
                    testTag = "connection-application-id",
                )
                ConnectionTextField(
                    value = packageName,
                    onValueChange = {
                        packageName = it.trim()
                        onClearFeedback()
                    },
                    label = "Android package",
                    supportingText = "Example: com.example.myapp",
                    enabled = !saving && !saved,
                    testTag = "connection-package-name",
                )
                ConnectionTextField(
                    value = signerSha256,
                    onValueChange = { candidate ->
                        normalizeSignerFingerprint(candidate)?.let { signerSha256 = it }
                        onClearFeedback()
                    },
                    label = "Signing certificate SHA-256",
                    supportingText = if (signerValid || signerSha256.isBlank()) {
                        "64 hexadecimal characters. Spaces and ':' are removed when pasted."
                    } else {
                        "Enter the complete 64-character SHA-256 fingerprint."
                    },
                    enabled = !saving && !saved,
                    testTag = "connection-signer-sha256",
                    isError = signerSha256.isNotBlank() && !signerValid,
                )
            }
        }
        item { Text("Use case", style = MaterialTheme.typography.titleMedium) }
        items(options, key = HarnessConnectionUseCaseOption::useCaseId) { option ->
            HarnessCard(
                emphasized = option.useCaseId == selectedUseCase.useCaseId,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connection-use-case-${option.useCaseId}")
                    .clickable(enabled = !saving && !saved) {
                        selectedUseCaseId = option.useCaseId
                        selectedPresetKey = option.presets.first().identityKey()
                        onClearFeedback()
                    },
            ) {
                Text(option.displayName, style = MaterialTheme.typography.titleMedium)
                Text(option.description, style = MaterialTheme.typography.bodySmall)
                if (option.useCaseId == selectedUseCase.useCaseId) {
                    HarnessStatusBadge("Selected", HarnessStatusTone.SUCCESS)
                }
            }
        }
        item { Text("Initial preset", style = MaterialTheme.typography.titleMedium) }
        items(selectedUseCase.presets, key = HarnessConnectionPresetOption::identityKey) { preset ->
            HarnessCard(
                emphasized = preset.identityKey() == selectedPreset.identityKey(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connection-preset-${preset.presetId}")
                    .clickable(enabled = !saving && !saved) {
                        selectedPresetKey = preset.identityKey()
                        onClearFeedback()
                    },
            ) {
                Text(preset.displayName, style = MaterialTheme.typography.titleMedium)
                Text(preset.description, style = MaterialTheme.typography.bodySmall)
                if (preset.identityKey() == selectedPreset.identityKey()) {
                    HarnessStatusBadge("Default on connection", HarnessStatusTone.SUCCESS)
                }
            }
        }
        item {
            HarnessCard(emphasized = true) {
                Text("Review connection", style = MaterialTheme.typography.titleMedium)
                HarnessKeyValueRow("Application", displayName.ifBlank { "Not set" })
                HarnessKeyValueRow("Package", packageName.ifBlank { "Not set" })
                HarnessKeyValueRow("Use case", selectedUseCase.displayName)
                HarnessKeyValueRow("Default preset", selectedPreset.displayName)
                Text(
                    "Creating this connection enables access immediately. You can disable it later without deleting its configuration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ConnectionMutationFeedback(
                state = mutationState,
                onReload = onReload,
                onClearFeedback = onClearFeedback,
                onDone = onDone,
            )
        }
        if (!saved) {
            item {
                HarnessPrimaryButton(
                    text = if (saving) "Creating connection…" else "Create & enable connection",
                    enabled = formValid && !saving,
                    modifier = Modifier.fillMaxWidth().testTag("connection-create"),
                    onClick = {
                        onCreate(
                            applicationId.trim(),
                            displayName.trim(),
                            packageName.trim(),
                            signerSha256,
                            selectedUseCase.useCaseId,
                            selectedPreset.presetId,
                            selectedPreset.revision,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    enabled: Boolean,
    testTag: String,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        enabled = enabled,
        isError = isError,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

@Composable
private fun ConnectionMutationFeedback(
    state: HarnessApplicationsMutationState,
    onReload: () -> Unit,
    onClearFeedback: () -> Unit,
    onDone: () -> Unit,
) {
    when (state) {
        HarnessApplicationsMutationState.Idle -> Unit

        HarnessApplicationsMutationState.Saving -> HarnessCard {
            HarnessStatusBadge("Saving", HarnessStatusTone.INFO)
            Text("Persisting the application identity, assignment and default preset.")
        }

        is HarnessApplicationsMutationState.Saved -> HarnessCard(emphasized = true) {
            HarnessStatusBadge("Connection ready", HarnessStatusTone.SUCCESS)
            Text(state.message)
            HarnessPrimaryButton("Done", onClick = onDone)
        }

        is HarnessApplicationsMutationState.Conflict -> HarnessRecoveryCard(
            title = "Configuration changed",
            detail = state.message,
            actionLabel = "Reload changes",
            onAction = onReload,
            tone = HarnessStatusTone.WARNING,
        )

        is HarnessApplicationsMutationState.Failed -> HarnessRecoveryCard(
            title = "Connection not created",
            detail = state.message,
            actionLabel = "Review fields",
            onAction = onClearFeedback,
            tone = HarnessStatusTone.ERROR,
        )
    }
}

internal fun normalizeSignerFingerprint(value: String): String? {
    val normalized = value.filterNot { it.isWhitespace() || it == ':' }.lowercase()
    return normalized.takeIf { it.length <= SHA256_FINGERPRINT_LENGTH && it.all(Char::isHexDigit) }
}

private fun Char.isHexDigit(): Boolean = isDigit() || lowercaseChar() in 'a'..'f'

private fun HarnessConnectionPresetOption.identityKey(): String = "$presetId:$revision"

private const val SHA256_FINGERPRINT_LENGTH = 64
