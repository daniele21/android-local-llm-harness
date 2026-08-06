from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


controller_path = PACKAGE / "PhonePlaygroundController.kt"
controller = controller_path.read_text(encoding="utf-8")
controller = replace_once(
    controller,
    '''internal class PhonePlaygroundController(private val runtimeGraph: HarnessRuntimeGraph, private val listener: (PlaygroundState) -> Unit) :
    AutoCloseable {''',
    '''internal class PhonePlaygroundController(private val runtimeGraph: HarnessRuntimeGraph, private val listener: (PlaygroundState) -> Unit) :
    PlaygroundEffects {''',
    "PhonePlaygroundController interface",
)
controller = replace_once(controller, "    fun snapshot(): PlaygroundState", "    override fun snapshot(): PlaygroundState", "snapshot override")
controller = replace_once(
    controller,
    "    fun start(model: ImportedPhoneModel, prompt: String, options: PlaygroundRequestOptions): Boolean",
    "    override fun start(model: ImportedPhoneModel, prompt: String, options: PlaygroundRequestOptions): Boolean",
    "start override",
)
controller = replace_once(controller, "    fun cancel(): Boolean", "    override fun cancel(): Boolean", "cancel override")
controller = replace_once(
    controller,
    "    fun releaseRuntime(onComplete: () -> Unit): Boolean",
    "    override fun releaseRuntime(onComplete: () -> Unit): Boolean",
    "release override",
)
controller_path.write_text(controller, encoding="utf-8")

activity_path = PACKAGE / "MainActivity.kt"
activity = activity_path.read_text(encoding="utf-8")
activity = replace_once(
    activity,
    "import androidx.activity.result.contract.ActivityResultContracts\n",
    "import androidx.activity.result.contract.ActivityResultContracts\nimport androidx.activity.viewModels\n",
    "viewModels import",
)
activity = replace_once(
    activity,
    "import androidx.core.view.WindowCompat\n",
    "import androidx.core.view.WindowCompat\nimport androidx.lifecycle.compose.collectAsStateWithLifecycle\n",
    "lifecycle compose import",
)
activity = replace_once(
    activity,
    "    private val diagnosticsExecutor = Executors.newSingleThreadExecutor()\n",
    "    private val diagnosticsExecutor = Executors.newSingleThreadExecutor()\n    private val harnessViewModel: HarnessViewModel by viewModels()\n",
    "ViewModel property",
)
for line in (
    "    private var playgroundState by mutableStateOf(PlaygroundState())\n",
    "    private var playgroundPrompt by mutableStateOf(DEFAULT_PROMPT)\n",
    "    private var playgroundMaxTokens by mutableStateOf(DEFAULT_MAX_OUTPUT_TOKENS)\n",
    "    private var playgroundTemperature by mutableStateOf(DEFAULT_TEMPERATURE)\n",
    "    private var playgroundSeed by mutableStateOf(DEFAULT_SEED)\n",
):
    activity = replace_once(activity, line, "", f"remove {line.strip()}")
activity = replace_once(
    activity,
    '''                    modelDistributionState = state
                    operationStatus = state.message
                    updateKeepScreenOn()''',
    '''                    modelDistributionState = state
                    operationStatus = state.message
                    harnessViewModel.dispatch(HarnessUiEvent.ModelDistributionChanged(state))
                    updateKeepScreenOn()''',
    "distribution state bridge",
)
activity = replace_once(
    activity,
    '''        playgroundController = PhonePlaygroundController(this, ::onPlaygroundStateChanged)
        playgroundState = playgroundController.snapshot()''',
    '''        playgroundController = PhonePlaygroundController(runtimeGraph, ::onPlaygroundStateChanged)
        harnessViewModel.attachPlaygroundEffects(playgroundController)''',
    "playground attachment",
)
activity = replace_once(
    activity,
    '''        modelDistributionController.close()
        playgroundController.close()
        controller.close()''',
    '''        modelDistributionController.close()
        harnessViewModel.detachPlaygroundEffects(playgroundController)
        playgroundController.close()
        controller.close()''',
    "playground detachment",
)
activity = replace_once(
    activity,
    '''            controllerBusy = busy
            updateKeepScreenOn()''',
    '''            controllerBusy = busy
            harnessViewModel.dispatch(HarnessUiEvent.ControllerBusyChanged(busy))
            updateKeepScreenOn()''',
    "controller busy bridge",
)
activity = replace_once(
    activity,
    '''            importedModel = model
            selectedRemovalConfirmationPending = false''',
    '''            importedModel = model
            harnessViewModel.dispatch(HarnessUiEvent.ModelChanged(model))
            selectedRemovalConfirmationPending = false''',
    "model bridge",
)
activity = replace_once(
    activity,
    '''            latestReport = report
            operationStatus = "Validation completed"''',
    '''            latestReport = report
            harnessViewModel.dispatch(HarnessUiEvent.ReportChanged(report))
            operationStatus = "Validation completed"''',
    "report bridge",
)
activity = replace_once(
    activity,
    '''        runOnUiThread {
            playgroundState = state
            updateKeepScreenOn()
            refreshDiagnostics()
        }''',
    '''        runOnUiThread {
            harnessViewModel.dispatch(HarnessUiEvent.PlaygroundChanged(state))
            updateKeepScreenOn()
            refreshDiagnostics()
        }''',
    "playground callback bridge",
)
activity = replace_once(
    activity,
    '''    private fun HarnessApp() {
        val navController = rememberNavController()''',
    '''    private fun HarnessApp() {
        val uiState by harnessViewModel.uiState.collectAsStateWithLifecycle()
        val navController = rememberNavController()''',
    "lifecycle-aware state collection",
)
activity = replace_once(
    activity,
    '''                        OverviewScreen(
                            onOpenPlayground = { navigate(HarnessDestination.PLAYGROUND) },''',
    '''                        OverviewScreen(
                            playground = uiState.playground,
                            onOpenPlayground = { navigate(HarnessDestination.PLAYGROUND) },''',
    "Overview state",
)
activity = replace_once(
    activity,
    '''                        PlaygroundScreen(
                            onOpenModels = { navigate(HarnessDestination.MODELS) },
                        )''',
    '''                        PlaygroundScreen(
                            state = uiState,
                            onOpenModels = { navigate(HarnessDestination.MODELS) },
                        )''',
    "Playground state",
)
activity = replace_once(
    activity,
    '''    private fun OverviewScreen(
        onOpenPlayground: () -> Unit,''',
    '''    private fun OverviewScreen(
        playground: PlaygroundState,
        onOpenPlayground: () -> Unit,''',
    "Overview signature",
)
activity = activity.replace("playgroundState.active", "playground.active", 1)
activity = activity.replace("playgroundState.metrics", "playground.metrics", 1)
activity = activity.replace("playgroundState.phase", "playground.phase", 2)

playground_start = activity.index("    @Composable\n    private fun PlaygroundScreen")
playground_end = activity.index("    @Composable\n    private fun ModelsScreen", playground_start)
new_playground_block = '''    @Composable
    private fun PlaygroundScreen(state: HarnessUiState, onOpenModels: () -> Unit) {
        var advancedVisible by rememberSaveable { mutableStateOf(true) }
        ScreenList(title = null) {
            item { DeviceOnlyStatus("Runs entirely on this device") }
            item { PlaygroundModelState(state.importedModel, onOpenModels) }
            item {
                PlaygroundPromptCard(
                    state = state,
                    advancedVisible = advancedVisible,
                    onToggleAdvanced = { advancedVisible = !advancedVisible },
                )
            }
            item { PlaygroundResponseCard(state.playground) }
        }
    }

    @Composable
    private fun PlaygroundModelState(model: ImportedPhoneModel?, onOpenModels: () -> Unit) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenModels),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HarnessDestinationIcon(HarnessDestination.MODELS, selected = model != null, modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (model == null) "NO ACTIVE MODEL" else "ACTIVE MODEL",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (model == null) HarnessColors.Warning else HarnessColors.Secondary,
                    )
                    Text(model?.fileName ?: "Choose a local GGUF model", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Change  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    @Composable
    private fun PlaygroundPromptCard(
        state: HarnessUiState,
        advancedVisible: Boolean,
        onToggleAdvanced: () -> Unit,
    ) {
        HarnessCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Prompt", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Clear",
                    modifier = Modifier.clickable { harnessViewModel.updatePlaygroundPrompt("") },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = state.playgroundPrompt,
                onValueChange = harnessViewModel::updatePlaygroundPrompt,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Prompt") },
                minLines = 4,
                enabled = !state.busy,
            )
            HarnessSecondaryButton(
                text = if (advancedVisible) "Generation settings  ·  Hide" else "Generation settings  ·  Show",
                modifier = Modifier.fillMaxWidth(),
                onClick = onToggleAdvanced,
            )
            if (advancedVisible) {
                PlaygroundGenerationSettings(state)
            }
            PlaygroundRunControls(state)
        }
    }

    @Composable
    private fun PlaygroundGenerationSettings(state: HarnessUiState) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.playgroundMaxTokens,
                onValueChange = harnessViewModel::updatePlaygroundMaxTokens,
                modifier = Modifier.weight(1f),
                label = { Text("Max tokens") },
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.playgroundTemperature,
                onValueChange = harnessViewModel::updatePlaygroundTemperature,
                modifier = Modifier.weight(1f),
                label = { Text("Temperature") },
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.playgroundSeed,
                onValueChange = harnessViewModel::updatePlaygroundSeed,
                modifier = Modifier.weight(1f),
                label = { Text("Seed") },
                enabled = !state.busy,
            )
        }
    }

    @Composable
    private fun PlaygroundRunControls(state: HarnessUiState) {
        val playground = state.playground
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HarnessPrimaryButton(
                text = if (playground.active) "Generating…" else "Run locally",
                enabled = state.importedModel != null && !state.busy,
                modifier = Modifier.weight(1f),
                onClick = ::startPlayground,
            )
            if (playground.active || playground.cancellationAvailable) {
                HarnessSecondaryButton(
                    text = "Stop",
                    enabled = playground.cancellationAvailable,
                    modifier = Modifier.weight(0.62f),
                    onClick = { harnessViewModel.cancelPlayground() },
                )
            }
        }
    }

    @Composable
    private fun PlaygroundResponseCard(playground: PlaygroundState) {
        HarnessCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Response", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (playground.active) {
                        "●  Streaming"
                    } else {
                        playground.phase.name.lowercase().replaceFirstChar(Char::uppercase)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (playground.active) HarnessColors.Secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(playground.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(
                    playground.output.ifBlank { "Generated output will appear here." },
                    fontFamily = FontFamily.Monospace,
                )
            }
            val metrics = playground.metrics
            HarnessMetricRow {
                HarnessMetric(
                    "TTFT",
                    metrics?.timeToFirstTokenMs?.let { "$it ms" } ?: "Unavailable",
                    Modifier.weight(1f),
                )
                HarnessMetric(
                    "Total",
                    metrics?.totalMs?.let { "$it ms" } ?: "Unavailable",
                    Modifier.weight(1f),
                )
                HarnessMetric(
                    "Decode",
                    metrics?.decodeTokensPerSecond?.let { "%.2f tok/s".format(it) } ?: "Unavailable",
                    Modifier.weight(1f),
                )
            }
        }
    }

'''
activity = activity[:playground_start] + new_playground_block + activity[playground_end:]

helper_start = activity.index("    private fun startPlayground(")
helper_end = activity.index("    private fun copyLog(", helper_start)
new_helper_block = '''    private fun startPlayground() {
        when (harnessViewModel.startPlayground()) {
            PlaygroundStartResult.STARTED -> Unit
            PlaygroundStartResult.MODEL_REQUIRED -> {
                Toast.makeText(this, "Select a local model first", Toast.LENGTH_SHORT).show()
            }
            PlaygroundStartResult.BUSY -> {
                Toast.makeText(this, "Wait for the active operation to finish", Toast.LENGTH_SHORT).show()
            }
            PlaygroundStartResult.INVALID_SETTINGS -> {
                Toast.makeText(this, "Invalid generation settings", Toast.LENGTH_LONG).show()
            }
            PlaygroundStartResult.CONTROLLER_UNAVAILABLE,
            PlaygroundStartResult.REJECTED,
            -> Toast.makeText(this, "Unable to start local inference", Toast.LENGTH_LONG).show()
        }
    }

    private fun afterPlaygroundRuntimeReleased(action: () -> Unit) {
        if (!harnessViewModel.releasePlaygroundRuntime { runOnUiThread(action) }) {
            Toast.makeText(this, "Cancel or wait for the active generation", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateKeepScreenOn() {
        if (harnessViewModel.uiState.value.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun diagnosticActionRunning(): Boolean = healthRunning || resourceCaptureRunning || benchmarkCaptureRunning

    private fun isBusy(): Boolean = harnessViewModel.uiState.value.busy

'''
activity = activity[:helper_start] + new_helper_block + activity[helper_end:]
for constant in (
    '        const val DEFAULT_MAX_OUTPUT_TOKENS = "128"\n',
    '        const val DEFAULT_TEMPERATURE = "0.2"\n',
    '        const val DEFAULT_SEED = "42"\n',
    '        const val DEFAULT_PROMPT = "Explain in two sentences why local inference improves privacy."\n',
):
    activity = replace_once(activity, constant, "", f"remove {constant.strip()}")
for forbidden in ("playgroundState", "playgroundPrompt", "playgroundMaxTokens", "playgroundTemperature", "playgroundSeed"):
    if forbidden in activity:
        raise RuntimeError(f"MainActivity still owns migrated field: {forbidden}")
activity_path.write_text(activity, encoding="utf-8")

print("Playground UI migration applied")
