from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY_PATH = ROOT / "apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/MainActivity.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


activity = ACTIVITY_PATH.read_text(encoding="utf-8")
activity = replace_once(
    activity,
    '''    private fun PlaygroundScreen(state: HarnessUiState, onOpenModels: () -> Unit) {
        var advancedVisible by rememberSaveable { mutableStateOf(true) }
        ScreenList(title = null) {''',
    '''    private fun PlaygroundScreen(state: HarnessUiState, onOpenModels: () -> Unit) {
        var advancedVisible by rememberSaveable { mutableStateOf(true) }
        val presentation = state.toPlaygroundPresentation()
        ScreenList(title = null) {''',
    "Playground presentation derivation",
)
activity = replace_once(
    activity,
    '''                PlaygroundPromptCard(
                    state = state,
                    advancedVisible = advancedVisible,''',
    '''                PlaygroundPromptCard(
                    state = state,
                    presentation = presentation,
                    advancedVisible = advancedVisible,''',
    "Playground prompt presentation",
)
activity = replace_once(
    activity,
    "            item { PlaygroundResponseCard(state.playground) }",
    "            item { PlaygroundResponseCard(presentation) }",
    "Playground response presentation",
)
activity = replace_once(
    activity,
    "    private fun PlaygroundPromptCard(state: HarnessUiState, advancedVisible: Boolean, onToggleAdvanced: () -> Unit) {",
    "    private fun PlaygroundPromptCard(state: HarnessUiState, presentation: PlaygroundPresentation, advancedVisible: Boolean, onToggleAdvanced: () -> Unit) {",
    "Playground prompt signature",
)
activity = replace_once(
    activity,
    "                PlaygroundGenerationSettings(state)",
    "                PlaygroundGenerationSettings(state, presentation)",
    "Playground settings presentation",
)
activity = replace_once(
    activity,
    "            PlaygroundRunControls(state)",
    "            PlaygroundRunControls(presentation)",
    "Playground controls presentation",
)
activity = replace_once(
    activity,
    "    private fun PlaygroundGenerationSettings(state: HarnessUiState) {",
    "    private fun PlaygroundGenerationSettings(state: HarnessUiState, presentation: PlaygroundPresentation) {",
    "Playground settings signature",
)
input_enabled_count = activity.count("enabled = !state.busy,")
if input_enabled_count != 4:
    raise RuntimeError(f"Playground input enabled expressions: expected 4, found {input_enabled_count}")
activity = activity.replace("enabled = !state.busy,", "enabled = presentation.inputsEnabled,")

controls_start = activity.index("    @Composable\n    private fun PlaygroundRunControls")
models_start = activity.index("    @Composable\n    private fun ModelsScreen", controls_start)
controls_and_response = '''    @Composable
    private fun PlaygroundRunControls(presentation: PlaygroundPresentation) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HarnessPrimaryButton(
                text = presentation.runLabel,
                enabled = presentation.runEnabled,
                modifier = Modifier.weight(1f),
                onClick = ::startPlayground,
            )
            if (presentation.stopVisible) {
                HarnessSecondaryButton(
                    text = "Stop",
                    enabled = presentation.stopEnabled,
                    modifier = Modifier.weight(0.62f),
                    onClick = { harnessViewModel.cancelPlayground() },
                )
            }
        }
    }

    @Composable
    private fun PlaygroundResponseCard(presentation: PlaygroundPresentation) {
        HarnessCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Response", style = MaterialTheme.typography.titleLarge)
                Text(
                    presentation.statusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = when (presentation.statusTone) {
                        PlaygroundPresentationTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                        PlaygroundPresentationTone.ACTIVE,
                        PlaygroundPresentationTone.SUCCESS,
                        -> HarnessColors.Secondary
                        PlaygroundPresentationTone.ERROR -> MaterialTheme.colorScheme.error
                        PlaygroundPresentationTone.WARNING -> HarnessColors.Warning
                    },
                )
            }
            Text(presentation.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(
                    presentation.responseText,
                    fontFamily = FontFamily.Monospace,
                )
            }
            HarnessMetricRow {
                HarnessMetric("TTFT", presentation.ttft, Modifier.weight(1f))
                HarnessMetric("Total", presentation.total, Modifier.weight(1f))
                HarnessMetric("Decode", presentation.decode, Modifier.weight(1f))
            }
        }
    }

'''
activity = activity[:controls_start] + controls_and_response + activity[models_start:]

playground_section_start = activity.index("    @Composable\n    private fun PlaygroundScreen")
playground_section_end = activity.index("    @Composable\n    private fun ModelsScreen", playground_section_start)
playground_section = activity[playground_section_start:playground_section_end]
for forbidden in ("playground.phase", "playground.active", "state.busy", "state.playground.metrics"):
    if forbidden in playground_section:
        raise RuntimeError(f"Playground UI still derives presentation directly: {forbidden}")

ACTIVITY_PATH.write_text(activity, encoding="utf-8")
print("Playground presentation wiring applied")
