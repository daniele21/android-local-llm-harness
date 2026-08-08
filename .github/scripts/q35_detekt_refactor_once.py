from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"pattern missing in {path}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1))


# Split trusted catalog identity from inspected GGUF structure.
replace_once(
    "models/model-install/src/main/kotlin/io/github/daniele21/localllm/install/Qwen35Compatibility.kt",
    '''    fun mismatch(release: CatalogModelRelease, metadata: GgufArtifactMetadata): String? = when {
        release.id != releaseId -> "release identity"
        release.artifact.digest != digest -> "artifact digest"
        release.artifact.sizeBytes != sizeBytes -> "artifact size"
        normalize(release.artifact.quantization) != normalize(quantization) -> "quantization"
        metadata.version != ggufVersion -> "GGUF version"
        normalize(metadata.architecture) != normalize(architecture) -> "GGUF architecture"
        metadata.name != name -> "GGUF model name"
        metadata.fileType != fileType -> "GGUF file type"
        metadata.keyValueCount != keyValueCount -> "GGUF key/value count"
        metadata.tensorCount != tensorCount -> "GGUF tensor count"
        metadata.contextLength != contextLength -> "GGUF context length"
        metadata.blockCount != blockCount -> "GGUF block count"
        metadata.embeddingLength != embeddingLength -> "GGUF embedding length"
        else -> null
    }

    private fun normalize(value: String?): String? = value?.trim()?.lowercase()?.replace('-', '_')''',
    '''    fun mismatch(release: CatalogModelRelease, metadata: GgufArtifactMetadata): String? =
        releaseMismatch(release) ?: metadataMismatch(metadata)

    private fun releaseMismatch(release: CatalogModelRelease): String? = when {
        release.id != releaseId -> "release identity"
        release.artifact.digest != digest -> "artifact digest"
        release.artifact.sizeBytes != sizeBytes -> "artifact size"
        normalize(release.artifact.quantization) != normalize(quantization) -> "quantization"
        else -> null
    }

    private fun metadataMismatch(metadata: GgufArtifactMetadata): String? = when {
        metadata.version != ggufVersion -> "GGUF version"
        normalize(metadata.architecture) != normalize(architecture) -> "GGUF architecture"
        metadata.name != name -> "GGUF model name"
        metadata.fileType != fileType -> "GGUF file type"
        metadata.keyValueCount != keyValueCount -> "GGUF key/value count"
        metadata.tensorCount != tensorCount -> "GGUF tensor count"
        metadata.contextLength != contextLength -> "GGUF context length"
        metadata.blockCount != blockCount -> "GGUF block count"
        metadata.embeddingLength != embeddingLength -> "GGUF embedding length"
        else -> null
    }

    private fun normalize(value: String?): String? = value?.trim()?.lowercase()?.replace('-', '_')''',
)

# Group scalar generation controls under one reducer branch.
ui_path = Path("apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/HarnessUiState.kt")
ui = ui_path.read_text()
ui = ui.replace(
    "    sealed interface Playground : HarnessUiEvent\n\n    sealed interface Diagnostics : HarnessUiEvent",
    "    sealed interface Playground : HarnessUiEvent\n\n    sealed interface PlaygroundControl : Playground\n\n    sealed interface Diagnostics : HarnessUiEvent",
    1,
)
event_declarations = {
    "data class PlaygroundMaxTokensChanged(val maxTokens: String) : Playground": "data class PlaygroundMaxTokensChanged(val maxTokens: String) : PlaygroundControl",
    "data class PlaygroundTemperatureChanged(val temperature: String) : Playground": "data class PlaygroundTemperatureChanged(val temperature: String) : PlaygroundControl",
    "data class PlaygroundTopPChanged(val topP: String) : Playground": "data class PlaygroundTopPChanged(val topP: String) : PlaygroundControl",
    "data class PlaygroundTopKChanged(val topK: String) : Playground": "data class PlaygroundTopKChanged(val topK: String) : PlaygroundControl",
    "data class PlaygroundMinPChanged(val minP: String) : Playground": "data class PlaygroundMinPChanged(val minP: String) : PlaygroundControl",
    "data class PlaygroundPresencePenaltyChanged(val presencePenalty: String) : Playground": "data class PlaygroundPresencePenaltyChanged(val presencePenalty: String) : PlaygroundControl",
    "data class PlaygroundThinkingModeChanged(val mode: ThinkingMode) : Playground": "data class PlaygroundThinkingModeChanged(val mode: ThinkingMode) : PlaygroundControl",
    "data class PlaygroundRepeatPenaltyChanged(val repeatPenalty: String) : Playground": "data class PlaygroundRepeatPenaltyChanged(val repeatPenalty: String) : PlaygroundControl",
    "data class PlaygroundRepeatLastNChanged(val repeatLastN: String) : Playground": "data class PlaygroundRepeatLastNChanged(val repeatLastN: String) : PlaygroundControl",
    "data class PlaygroundSeedChanged(val seed: String) : Playground": "data class PlaygroundSeedChanged(val seed: String) : PlaygroundControl",
    "data class PlaygroundContextChanged(val context: String) : Playground": "data class PlaygroundContextChanged(val context: String) : PlaygroundControl",
}
for old, new in event_declarations.items():
    if old not in ui:
        raise SystemExit(f"playground event declaration missing: {old}")
    ui = ui.replace(old, new, 1)
start = ui.index("    private fun reducePlayground(state: HarnessUiState, event: HarnessUiEvent.Playground): HarnessUiState = when (event) {")
end = ui.index("\n\n    private fun HarnessUiState.applyPreset", start)
ui_reducer = '''    private fun reducePlayground(state: HarnessUiState, event: HarnessUiEvent.Playground): HarnessUiState = when (event) {
        is HarnessUiEvent.PlaygroundChanged -> state.copy(playground = event.state)
        is HarnessUiEvent.PlaygroundPromptChanged -> state.copy(playgroundPrompt = event.prompt)
        is HarnessUiEvent.PlaygroundPresetChanged -> state.applyPreset(event.preset)
        is HarnessUiEvent.PlaygroundControl -> reducePlaygroundControl(state, event)
    }

    private fun reducePlaygroundControl(
        state: HarnessUiState,
        event: HarnessUiEvent.PlaygroundControl,
    ): HarnessUiState = when (event) {
        is HarnessUiEvent.PlaygroundMaxTokensChanged -> state.copy(playgroundMaxTokens = event.maxTokens, playgroundPreset = "")
        is HarnessUiEvent.PlaygroundTemperatureChanged -> state.copy(
            playgroundTemperature = event.temperature,
            playgroundPreset = "",
        )
        is HarnessUiEvent.PlaygroundTopPChanged -> state.copy(playgroundTopP = event.topP, playgroundPreset = "")
        is HarnessUiEvent.PlaygroundTopKChanged -> state.copy(playgroundTopK = event.topK, playgroundPreset = "")
        is HarnessUiEvent.PlaygroundMinPChanged -> state.copy(playgroundMinP = event.minP, playgroundPreset = "")
        is HarnessUiEvent.PlaygroundPresencePenaltyChanged -> state.copy(
            playgroundPresencePenalty = event.presencePenalty,
            playgroundPreset = "",
        )
        is HarnessUiEvent.PlaygroundThinkingModeChanged -> state.copy(
            playgroundThinkingMode = event.mode,
            playgroundPreset = "",
        )
        is HarnessUiEvent.PlaygroundRepeatPenaltyChanged -> state.copy(
            playgroundRepeatPenalty = event.repeatPenalty,
            playgroundPreset = "",
        )
        is HarnessUiEvent.PlaygroundRepeatLastNChanged -> state.copy(
            playgroundRepeatLastN = event.repeatLastN,
            playgroundPreset = "",
        )
        is HarnessUiEvent.PlaygroundSeedChanged -> state.copy(playgroundSeed = event.seed, playgroundPreset = "")
        is HarnessUiEvent.PlaygroundContextChanged -> state.copy(playgroundContext = event.context, playgroundPreset = "")
    }'''
ui_path.write_text(ui[:start] + ui_reducer + ui[end:])

# Split the Compose settings surface into focused sections, preserving visible text and test tags.
main_path = Path("apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/MainActivity.kt")
main = main_path.read_text()
start = main.index("    @Composable\n    private fun PlaygroundGenerationSettings(state: HarnessUiState, presentation: PlaygroundPresentation) {")
end = main.index("\n\n    @Composable\n    private fun PlaygroundRunControls", start)
main_replacement = '''    @Composable
    private fun PlaygroundGenerationSettings(state: HarnessUiState, presentation: PlaygroundPresentation) {
        val selectedPreset = playgroundPresetOptions.firstOrNull { it.id == state.playgroundPreset }
        val basePreset = playgroundPresetOptions.firstOrNull { it.id == state.playgroundBasePreset }
        PlaygroundPresetControls(state, presentation, selectedPreset, basePreset)
        PlaygroundThinkingControls(state, presentation)
        val temperature = playgroundTemperature(state)
        PlaygroundPrimarySamplingControls(state, presentation, temperature)
        PlaygroundSamplingFields(state, presentation)
        PlaygroundPenaltyControls(state, presentation, temperature)
        PlaygroundSeedAndContextControls(state, presentation, temperature)
    }

    @Composable
    private fun PlaygroundPresetControls(
        state: HarnessUiState,
        presentation: PlaygroundPresentation,
        selectedPreset: PlaygroundPresetOption?,
        basePreset: PlaygroundPresetOption?,
    ) {
        Text(
            text = selectedPreset?.let { "Preset · ${it.label}" }
                ?: "Preset · Personalizzato${basePreset?.let { " · Basato su ${it.label}" }.orEmpty()}",
            style = MaterialTheme.typography.titleSmall,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(playgroundPresetOptions) { preset ->
                FilterChip(
                    selected = state.playgroundPreset == preset.id,
                    onClick = { harnessViewModel.updatePlaygroundPreset(preset.id) },
                    label = { Text(preset.label) },
                    enabled = presentation.inputsEnabled,
                )
            }
        }
        Text(
            text = selectedPreset?.description ?: basePreset?.description.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    @Composable
    private fun PlaygroundThinkingControls(state: HarnessUiState, presentation: PlaygroundPresentation) {
        Text("Thinking", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.playgroundThinkingMode == ThinkingMode.DISABLED,
                onClick = { harnessViewModel.updatePlaygroundThinkingMode(ThinkingMode.DISABLED) },
                label = { Text("Off") },
                enabled = presentation.inputsEnabled,
            )
            FilterChip(
                selected = state.playgroundThinkingMode == ThinkingMode.ENABLED,
                onClick = { harnessViewModel.updatePlaygroundThinkingMode(ThinkingMode.ENABLED) },
                label = { Text("On") },
                enabled = presentation.inputsEnabled,
                modifier = Modifier.testTag("playground-thinking-on"),
            )
        }
    }

    private fun playgroundTemperature(state: HarnessUiState): Float =
        state.playgroundTemperature.toFloatOrNull()?.coerceIn(0f, 2f) ?: 0f

    @Composable
    private fun PlaygroundPrimarySamplingControls(
        state: HarnessUiState,
        presentation: PlaygroundPresentation,
        temperature: Float,
    ) {
        Text("Temperature · ${state.playgroundTemperature}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = temperature,
            onValueChange = { harnessViewModel.updatePlaygroundTemperature(formatControlValue(it)) },
            valueRange = 0f..2f,
            enabled = presentation.inputsEnabled,
            modifier = Modifier.fillMaxWidth().testTag("playground-temperature-slider"),
        )
        val topP = state.playgroundTopP.toFloatOrNull()?.coerceIn(0.01f, 1f) ?: 0.9f
        Text("Top-p · ${state.playgroundTopP}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = topP,
            onValueChange = { harnessViewModel.updatePlaygroundTopP(formatControlValue(it)) },
            valueRange = 0.01f..1f,
            enabled = presentation.inputsEnabled && temperature != 0f,
            modifier = Modifier.fillMaxWidth().testTag("playground-top-p-slider"),
        )
    }

    @Composable
    private fun PlaygroundSamplingFields(state: HarnessUiState, presentation: PlaygroundPresentation) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.playgroundMaxTokens,
                onValueChange = harnessViewModel::updatePlaygroundMaxTokens,
                modifier = Modifier.weight(1f),
                label = { Text("Max output tokens") },
                enabled = presentation.inputsEnabled,
            )
            OutlinedTextField(
                value = state.playgroundTemperature,
                onValueChange = harnessViewModel::updatePlaygroundTemperature,
                modifier = Modifier.weight(1f),
                label = { Text("Temperature") },
                enabled = presentation.inputsEnabled,
            )
        }
        val samplingEnabled = presentation.inputsEnabled && state.playgroundTemperature.toFloatOrNull() != 0f
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.playgroundTopP,
                onValueChange = harnessViewModel::updatePlaygroundTopP,
                modifier = Modifier.weight(1f),
                label = { Text("Top-p") },
                enabled = samplingEnabled,
            )
            OutlinedTextField(
                value = state.playgroundTopK,
                onValueChange = harnessViewModel::updatePlaygroundTopK,
                modifier = Modifier.weight(1f),
                label = { Text("Top-k") },
                enabled = samplingEnabled,
            )
            OutlinedTextField(
                value = state.playgroundSeed,
                onValueChange = harnessViewModel::updatePlaygroundSeed,
                modifier = Modifier.weight(1f),
                label = { Text("Seed · blank = random") },
                enabled = samplingEnabled,
            )
        }
    }

    @Composable
    private fun PlaygroundPenaltyControls(
        state: HarnessUiState,
        presentation: PlaygroundPresentation,
        temperature: Float,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.playgroundMinP,
                onValueChange = harnessViewModel::updatePlaygroundMinP,
                modifier = Modifier.weight(1f).testTag("playground-min-p"),
                label = { Text("Min-p") },
                enabled = presentation.inputsEnabled && temperature != 0f,
            )
            OutlinedTextField(
                value = state.playgroundPresencePenalty,
                onValueChange = harnessViewModel::updatePlaygroundPresencePenalty,
                modifier = Modifier.weight(1f).testTag("playground-presence-penalty"),
                label = { Text("Presence penalty") },
                enabled = presentation.inputsEnabled,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.playgroundRepeatPenalty,
                onValueChange = harnessViewModel::updatePlaygroundRepeatPenalty,
                modifier = Modifier.weight(1f).testTag("playground-repeat-penalty"),
                label = { Text("Repeat penalty") },
                supportingText = { Text("1 = off") },
                enabled = presentation.inputsEnabled,
            )
            OutlinedTextField(
                value = state.playgroundRepeatLastN,
                onValueChange = harnessViewModel::updatePlaygroundRepeatLastN,
                modifier = Modifier.weight(1f).testTag("playground-repeat-last-n"),
                label = { Text("Repeat last N") },
                supportingText = { Text("0 = off") },
                enabled = presentation.inputsEnabled,
            )
        }
    }

    @Composable
    private fun PlaygroundSeedAndContextControls(
        state: HarnessUiState,
        presentation: PlaygroundPresentation,
        temperature: Float,
    ) {
        val samplingEnabled = presentation.inputsEnabled && temperature != 0f
        Text("Seed policy", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.playgroundSeed.isBlank(),
                onClick = { harnessViewModel.updatePlaygroundSeed("") },
                label = { Text("Random each run") },
                enabled = samplingEnabled,
            )
            FilterChip(
                selected = state.playgroundSeed.isNotBlank(),
                onClick = { if (state.playgroundSeed.isBlank()) harnessViewModel.updatePlaygroundSeed("42") },
                label = { Text("Fixed") },
                enabled = samplingEnabled,
            )
        }
        Text("Context policy", style = MaterialTheme.typography.labelLarge)
        FilterChip(
            selected = state.playgroundContext.isBlank(),
            onClick = { harnessViewModel.updatePlaygroundContext("") },
            label = { Text("Auto") },
            enabled = presentation.inputsEnabled,
        )
        OutlinedTextField(
            value = state.playgroundContext,
            onValueChange = harnessViewModel::updatePlaygroundContext,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Context size · blank = Auto") },
            supportingText = { Text("Manual values are applied exactly; insufficient context fails without truncation.") },
            enabled = presentation.inputsEnabled,
        )
    }'''
main_path.write_text(main[:start] + main_replacement + main[end:])

# Split generation bound validation into focused predicates.
runtime_path = Path("core/runtime-core/src/main/kotlin/io/github/daniele21/localllm/runtime/RuntimeOrchestrator.kt")
runtime = runtime_path.read_text()
start = runtime.index('    @Suppress("ComplexCondition")\n    private fun validateGenerationValues(')
end = runtime.index("\n\n    private fun validateOutputConstraint", start)
runtime_replacement = '''    private fun validateGenerationValues(
        maxOutputTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        presencePenalty: Float,
        repeatPenalty: Float,
        repeatLastN: Int,
    ) {
        val valid = outputAndTemperatureValid(maxOutputTokens, temperature) &&
            samplingValuesValid(topP, topK, minP) &&
            penaltyValuesValid(presencePenalty, repeatPenalty, repeatLastN)
        if (!valid) {
            throw GenerationPlanningException(
                ConfigurationErrorCode.INVALID_GENERATION_CONFIGURATION,
                "Generation settings are outside the supported bounds",
            )
        }
    }

    private fun outputAndTemperatureValid(maxOutputTokens: Int, temperature: Float): Boolean =
        maxOutputTokens in 1..MAX_OUTPUT_TOKENS && temperature.isFinite() && temperature in 0f..2f

    private fun samplingValuesValid(topP: Float, topK: Int, minP: Float): Boolean =
        topP.isFinite() && topP > 0f && topP <= 1f &&
            topK in 0..MAX_TOP_K &&
            minP.isFinite() && minP in 0f..1f

    private fun penaltyValuesValid(presencePenalty: Float, repeatPenalty: Float, repeatLastN: Int): Boolean =
        presencePenalty.isFinite() && presencePenalty in 0f..2f &&
            repeatPenalty.isFinite() && repeatPenalty in MIN_REPEAT_PENALTY..MAX_REPEAT_PENALTY &&
            repeatLastN in 0..MAX_REPEAT_LAST_N &&
            (repeatPenalty == MIN_REPEAT_PENALTY || repeatLastN != 0)'''
runtime_path.write_text(runtime[:start] + runtime_replacement + runtime[end:])
