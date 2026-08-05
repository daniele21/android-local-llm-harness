@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.observability.android.AndroidResourceSnapshotProvider
import io.github.daniele21.localllm.observability.android.ResourceSnapshotRecorder
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessMetric
import io.github.daniele21.localllm.ui.designsystem.HarnessMetricRow
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessTheme
import java.util.concurrent.Executors

@Suppress("TooManyFunctions", "LongMethod", "LargeClass")
class MainActivity :
    ComponentActivity(),
    PhoneTestListener {
    private lateinit var runtimeGraph: HarnessRuntimeGraph
    private lateinit var diagnosticsSource: HarnessDiagnosticsSource
    private lateinit var healthSource: HarnessHealthSource
    private lateinit var resourceSource: HarnessResourceSource
    private lateinit var benchmarkSource: HarnessBenchmarkSource
    private lateinit var logSource: HarnessLogSource
    private lateinit var controller: PhoneTestController
    private lateinit var modelDistributionController: PhoneModelDistributionController
    private lateinit var selectedModelManagement: PhoneModelManagementGateway
    private lateinit var playgroundController: PhonePlaygroundController
    private val diagnosticsExecutor = Executors.newSingleThreadExecutor()

    private var importedModel by mutableStateOf<ImportedPhoneModel?>(null)
    private var modelDistributionState by mutableStateOf(PhoneModelDistributionState())
    private var latestReport by mutableStateOf("")
    private var controllerBusy by mutableStateOf(false)
    private var healthRunning by mutableStateOf(false)
    private var resourceCaptureRunning by mutableStateOf(false)
    private var benchmarkCaptureRunning by mutableStateOf(false)
    private var operationStatus by mutableStateOf("Ready")
    private var playgroundState by mutableStateOf(PlaygroundState())
    private var diagnosticsState by mutableStateOf(DiagnosticsUiState(null, emptyList(), emptyList()))
    private var benchmarkState by mutableStateOf(BenchmarkUiState())
    private var logFilter by mutableStateOf(DiagnosticsLogFilter())
    private var logState by mutableStateOf(DiagnosticsLogUiState())
    private var selectedRequestTimeline by mutableStateOf<DiagnosticsRequestTimelineUi?>(null)
    private var diagnosticsSection by mutableStateOf(DiagnosticsSection.RUNS)
    private var playgroundPrompt by mutableStateOf(DEFAULT_PROMPT)
    private var playgroundMaxTokens by mutableStateOf(DEFAULT_MAX_OUTPUT_TOKENS)
    private var playgroundTemperature by mutableStateOf(DEFAULT_TEMPERATURE)
    private var playgroundSeed by mutableStateOf(DEFAULT_SEED)
    private var selectedRemovalConfirmationPending by mutableStateOf(false)

    @Volatile
    private var selectedModelForDiagnostics: ImportedPhoneModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestReport = savedInstanceState?.getString(STATE_REPORT).orEmpty()
        runtimeGraph = HarnessRuntimeGraph.from(this)
        resourceSource = HarnessResourceSource(
            recorder = ResourceSnapshotRecorder(
                AndroidResourceSnapshotProvider(this),
                runtimeGraph.telemetryRepository,
            ),
            telemetryRepository = runtimeGraph.telemetryRepository,
        )
        diagnosticsSource = HarnessDiagnosticsSource(
            telemetryRepository = runtimeGraph.telemetryRepository,
            runtimeSnapshot = runtimeGraph::runtimeSnapshot,
            resourceSnapshots = resourceSource::recent,
        )
        healthSource = HarnessHealthSource(
            modelStore = runtimeGraph.modelStore,
            telemetryRepository = runtimeGraph.telemetryRepository,
            selectedModel = { selectedModelForDiagnostics },
            runtimeState = { runtimeGraph.runtimeSnapshot()?.state },
        )
        benchmarkSource = HarnessBenchmarkSource(runtimeGraph.telemetryRepository) {
            selectedModelForDiagnostics
        }
        logSource = HarnessLogSource(runtimeGraph.telemetryRepository)
        controller = PhoneTestController(this, this)
        selectedModelManagement = ModelStorePhoneModelManagementControl(
            modelStore = runtimeGraph.modelStore,
            protectedModelDigest = { runtimeGraph.loadedModelDigest },
            removeMetadata = { true },
        )
        modelDistributionController = PhoneModelDistributionController.from(
            context = this,
            runtimeGraph = runtimeGraph,
            listener = PhoneModelDistributionListener { state ->
                runOnUiThread {
                    modelDistributionState = state
                    operationStatus = state.message
                    updateKeepScreenOn()
                }
            },
        )
        playgroundController = PhonePlaygroundController(this, ::onPlaygroundStateChanged)
        playgroundState = playgroundController.snapshot()
        refreshDiagnostics()

        setContent {
            HarnessTheme { HarnessApp() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_REPORT, latestReport)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        diagnosticsExecutor.shutdownNow()
        modelDistributionController.close()
        playgroundController.close()
        controller.close()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    override fun onBusyChanged(busy: Boolean) {
        runOnUiThread {
            controllerBusy = busy
            updateKeepScreenOn()
            refreshDiagnostics()
        }
    }

    override fun onProgress(message: String) {
        runOnUiThread {
            operationStatus = message
            refreshDiagnostics()
        }
    }

    override fun onModelChanged(model: ImportedPhoneModel?) {
        selectedModelForDiagnostics = model
        runOnUiThread {
            importedModel = model
            selectedRemovalConfirmationPending = false
            if (::modelDistributionController.isInitialized) {
                modelDistributionController.refresh()
            }
            refreshDiagnostics()
        }
    }

    override fun onReport(report: String) {
        runOnUiThread {
            latestReport = report
            operationStatus = "Validation completed"
            refreshDiagnostics()
        }
    }

    private fun onPlaygroundStateChanged(state: PlaygroundState) {
        runOnUiThread {
            playgroundState = state
            updateKeepScreenOn()
            refreshDiagnostics()
        }
    }

    private fun verifySelectedModel() {
        val model = importedModel ?: return
        if (isBusy()) return
        operationStatus = "Verifying selected model integrity…"
        diagnosticsExecutor.execute {
            val outcome = selectedModelManagement.verify(model.digest)
            runOnUiThread {
                operationStatus = outcome.detail
                modelDistributionController.refresh()
                refreshDiagnostics()
            }
        }
    }

    private fun refreshDiagnostics() {
        if (::diagnosticsSource.isInitialized) diagnosticsState = diagnosticsSource.snapshot()
        if (::benchmarkSource.isInitialized) {
            benchmarkState = benchmarkSource.snapshot(benchmarkState.captureDetail)
        }
        if (::logSource.isInitialized) {
            logState = logSource.snapshot(logFilter)
            selectedRequestTimeline = selectedRequestTimeline?.let { timeline ->
                logSource.requestTimeline(timeline.requestId)
            }
        }
    }

    private fun runHealthChecks() {
        if (diagnosticActionRunning() || isBusy()) return
        healthRunning = true
        operationStatus = "Running health checks…"
        diagnosticsExecutor.execute {
            val result = runCatching { healthSource.runAll() }
            runOnUiThread {
                healthRunning = false
                operationStatus = result.fold(
                    onSuccess = { "Health checks completed: ${it.status.name}" },
                    onFailure = { "Health checks could not be completed" },
                )
                refreshDiagnostics()
            }
        }
    }

    private fun captureResourceSnapshot() {
        if (diagnosticActionRunning() || isBusy()) return
        resourceCaptureRunning = true
        operationStatus = "Capturing device resources…"
        diagnosticsExecutor.execute {
            val result = runCatching(resourceSource::capture)
            runOnUiThread {
                resourceCaptureRunning = false
                operationStatus = if (result.isSuccess) {
                    "Resource snapshot captured"
                } else {
                    "Resource snapshot could not be captured"
                }
                refreshDiagnostics()
            }
        }
    }

    private fun captureBenchmarkBaselines() {
        if (diagnosticActionRunning() || isBusy()) return
        benchmarkCaptureRunning = true
        operationStatus = "Capturing benchmark baselines…"
        diagnosticsExecutor.execute {
            val result = runCatching(benchmarkSource::captureEligible)
            runOnUiThread {
                benchmarkCaptureRunning = false
                benchmarkState = result.getOrElse {
                    BenchmarkUiState(sourceError = "Benchmark capture could not be completed.")
                }
                operationStatus = benchmarkState.captureDetail
                    ?: benchmarkState.sourceError
                    ?: "Benchmark capture completed"
                refreshDiagnostics()
            }
        }
    }

    private fun captureBenchmarkBaseline(stableId: String) {
        if (diagnosticActionRunning() || isBusy()) return
        benchmarkCaptureRunning = true
        operationStatus = "Capturing selected benchmark baseline…"
        diagnosticsExecutor.execute {
            val result = runCatching { benchmarkSource.capture(stableId) }
            runOnUiThread {
                benchmarkCaptureRunning = false
                benchmarkState = result.getOrElse {
                    BenchmarkUiState(sourceError = "Benchmark capture could not be completed.")
                }
                operationStatus = benchmarkState.captureDetail
                    ?: benchmarkState.sourceError
                    ?: "Benchmark capture completed"
                refreshDiagnostics()
            }
        }
    }

    private fun selectDiagnosticsSection(section: DiagnosticsSection) {
        diagnosticsSection = section
        if (section != DiagnosticsSection.LOGS) selectedRequestTimeline = null
    }

    private fun updateLogFilter(filter: DiagnosticsLogFilter) {
        logFilter = filter
        logState = logSource.snapshot(filter)
    }

    private fun openRequestTimeline(requestId: String) {
        selectedRequestTimeline = logSource.requestTimeline(requestId)
        diagnosticsSection = DiagnosticsSection.LOGS
    }

    private fun closeRequestTimeline() {
        selectedRequestTimeline = null
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HarnessApp() {
        var destination by rememberSaveable { mutableStateOf(HarnessDestination.OVERVIEW) }
        val expanded = LocalConfiguration.current.screenWidthDp >= 720
        val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                afterPlaygroundRuntimeReleased {
                    controller.importModel(
                        uri = uri,
                        architecture = DEFAULT_ARCHITECTURE,
                        quantization = DEFAULT_QUANTIZATION,
                    )
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Harness", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Local AI Console",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        HarnessSecondaryButton("Settings") { destination = HarnessDestination.SETTINGS }
                    },
                )
            },
            bottomBar = {
                if (!expanded && destination != HarnessDestination.SETTINGS) {
                    NavigationBar {
                        HarnessDestination.main.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { Text(item.shortLabel) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (expanded) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        HarnessDestination.main.forEach { item ->
                            NavigationRailItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { Text(item.shortLabel) },
                                label = { Text(item.label) },
                            )
                        }
                        NavigationRailItem(
                            selected = destination == HarnessDestination.SETTINGS,
                            onClick = { destination = HarnessDestination.SETTINGS },
                            icon = { Text("S") },
                            label = { Text("Settings") },
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    when (destination) {
                        HarnessDestination.OVERVIEW -> OverviewScreen(
                            onOpenPlayground = { destination = HarnessDestination.PLAYGROUND },
                            onImport = { modelPicker.launch(MODEL_MIME_TYPES) },
                        )

                        HarnessDestination.PLAYGROUND -> PlaygroundScreen()

                        HarnessDestination.MODELS -> ModelsScreen(
                            onImport = { modelPicker.launch(MODEL_MIME_TYPES) },
                        )

                        HarnessDestination.DIAGNOSTICS -> DiagnosticsScreen()

                        HarnessDestination.SETTINGS -> SettingsScreen {
                            destination = HarnessDestination.OVERVIEW
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun OverviewScreen(onOpenPlayground: () -> Unit, onImport: () -> Unit) {
        ScreenList("Overview") {
            item {
                HarnessCard {
                    Text(
                        if (importedModel == null) "No model selected" else "Model ready",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        importedModel?.fileName ?: "Import a GGUF model to start local inference.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HarnessMetricRow {
                        HarnessMetric(
                            "Runtime",
                            if (playgroundState.phase == PlaygroundPhase.IDLE) {
                                "Idle"
                            } else {
                                playgroundState.phase.name
                            },
                            Modifier.weight(1f),
                        )
                        HarnessMetric("Privacy", "On-device", Modifier.weight(1f))
                    }
                    HarnessPrimaryButton(
                        "Open playground",
                        enabled = importedModel != null,
                        onClick = onOpenPlayground,
                    )
                    HarnessSecondaryButton("Import GGUF", enabled = !isBusy(), onClick = onImport)
                }
            }
            item {
                HarnessCard {
                    Text("Latest performance", style = MaterialTheme.typography.titleMedium)
                    val metrics = playgroundState.metrics
                    HarnessMetricRow {
                        HarnessMetric(
                            "TTFT",
                            metrics?.timeToFirstTokenMs?.let { "$it ms" } ?: "Unavailable",
                            Modifier.weight(1f),
                        )
                        HarnessMetric(
                            "Decode",
                            metrics?.decodeTokensPerSecond?.let { "%.2f tok/s".format(it) }
                                ?: "Unavailable",
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PlaygroundScreen() {
        ScreenList("Playground") {
            item {
                HarnessCard {
                    Text("One-shot local inference", style = MaterialTheme.typography.titleLarge)
                    Text("Prompts and generated output stay in process memory and are not persisted.")
                    OutlinedTextField(
                        value = playgroundPrompt,
                        onValueChange = { playgroundPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Prompt") },
                        minLines = 5,
                        enabled = !isBusy(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = playgroundMaxTokens,
                            onValueChange = { playgroundMaxTokens = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Max tokens") },
                        )
                        OutlinedTextField(
                            value = playgroundTemperature,
                            onValueChange = { playgroundTemperature = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Temperature") },
                        )
                        OutlinedTextField(
                            value = playgroundSeed,
                            onValueChange = { playgroundSeed = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Seed") },
                        )
                    }
                    HarnessPrimaryButton(
                        "Generate locally",
                        enabled = importedModel != null && !isBusy(),
                    ) {
                        startPlayground(
                            playgroundPrompt,
                            playgroundMaxTokens,
                            playgroundTemperature,
                            playgroundSeed,
                        )
                    }
                    HarnessSecondaryButton(
                        "Cancel generation",
                        enabled = playgroundState.cancellationAvailable,
                    ) {
                        playgroundController.cancel()
                    }
                }
            }
            item {
                HarnessCard {
                    Text(playgroundState.detail, style = MaterialTheme.typography.titleMedium)
                    SelectionContainer {
                        Text(
                            playgroundState.output.ifBlank { "Generated output will appear here." },
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    val metrics = playgroundState.metrics
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
                    }
                }
            }
        }
    }

    @Composable
    private fun ModelsScreen(onImport: () -> Unit) {
        ScreenList("Models") {
            item {
                PhoneModelDistributionCatalog(
                    state = modelDistributionState,
                    actions =
                    PhoneModelDistributionActions(
                        download = modelDistributionController::download,
                        cancelDownload = modelDistributionController::cancelDownload,
                        install = modelDistributionController::install,
                        verifyInstalled = modelDistributionController::verifyInstalled,
                        requestRemove = modelDistributionController::requestRemove,
                        cancelRemove = modelDistributionController::cancelRemove,
                        confirmRemove = modelDistributionController::confirmRemove,
                        selectInstalled = { metadata ->
                            afterPlaygroundRuntimeReleased {
                                controller.selectInstalledModel(metadata.asImportedPhoneModel())
                            }
                        },
                    ),
                )
            }
            item {
                HarnessCard {
                    Text("Manual GGUF import", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Use Android's document picker for a local GGUF that is not present in the curated catalog.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HarnessPrimaryButton("Import GGUF", enabled = !isBusy(), onClick = onImport)
                }
            }
            item {
                val model = importedModel
                if (model == null) {
                    HarnessCard {
                        Text("No model selected", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Downloading and installing does not activate a model. Choose Use in Playground " +
                                "on an installed catalog model, or import a GGUF manually.",
                        )
                    }
                } else {
                    HarnessCard {
                        Text("Selected for local inference", style = MaterialTheme.typography.titleMedium)
                        Text(model.fileName, style = MaterialTheme.typography.titleLarge)
                        HarnessMetricRow {
                            HarnessMetric("Architecture", model.architecture, Modifier.weight(1f))
                            HarnessMetric("Quantization", model.quantization, Modifier.weight(1f))
                        }
                        HarnessMetric("SHA-256", model.digest.sha256.take(24) + "…")
                        HarnessMetric("Size", formatBytes(model.sizeBytes))
                        HarnessSecondaryButton(
                            "Verify integrity",
                            enabled = !isBusy(),
                            onClick = ::verifySelectedModel,
                        )
                        if (selectedRemovalConfirmationPending) {
                            Text(
                                "Removal permanently deletes the app-private model copy.",
                                color = MaterialTheme.colorScheme.error,
                            )
                            HarnessPrimaryButton(
                                "Confirm removal",
                                enabled = !isBusy(),
                            ) {
                                afterPlaygroundRuntimeReleased {
                                    selectedRemovalConfirmationPending = false
                                    controller.removeModel()
                                }
                            }
                            HarnessSecondaryButton("Cancel removal") {
                                selectedRemovalConfirmationPending = false
                            }
                        } else {
                            HarnessSecondaryButton("Remove model", enabled = !isBusy()) {
                                selectedRemovalConfirmationPending = true
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DiagnosticsScreen() {
        ScreenList("Diagnostics") {
            item {
                DiagnosticsSectionSelector(
                    selected = diagnosticsSection,
                    onSelected = ::selectDiagnosticsSection,
                )
            }
            runtimeDiagnostics()
            when (diagnosticsSection) {
                DiagnosticsSection.RUNS -> runDiagnostics()

                DiagnosticsSection.HEALTH -> healthDiagnostics()

                DiagnosticsSection.RESOURCES -> resourceDiagnostics()

                DiagnosticsSection.BENCHMARKS -> benchmarkDiagnostics()

                DiagnosticsSection.LOGS -> logDiagnostics(
                    state = logState,
                    filter = logFilter,
                    timeline = selectedRequestTimeline,
                    onFilterChange = ::updateLogFilter,
                    onOpenTimeline = ::openRequestTimeline,
                    onCloseTimeline = ::closeRequestTimeline,
                    onCopyLog = ::copyLog,
                )

                DiagnosticsSection.VALIDATION -> validationDiagnostics()
            }
        }
    }

    private fun androidx.compose.foundation.lazy.LazyListScope.runtimeDiagnostics() {
        item {
            HarnessCard {
                Text("Runtime status", style = MaterialTheme.typography.titleLarge)
                Text(operationStatus)
                HarnessMetricRow {
                    HarnessMetric(
                        "Runtime",
                        diagnosticsState.runtime?.state?.name ?: "Unavailable",
                        Modifier.weight(1f),
                    )
                    HarnessMetric(
                        "Active sessions",
                        diagnosticsState.runtime?.activeSessions?.toString() ?: "Unavailable",
                        Modifier.weight(1f),
                    )
                }
                HarnessSecondaryButton("Refresh diagnostics", onClick = ::refreshDiagnostics)
            }
        }
    }

    private fun androidx.compose.foundation.lazy.LazyListScope.healthDiagnostics() {
        item {
            HarnessCard {
                Text("Health", style = MaterialTheme.typography.titleLarge)
                HarnessMetricRow {
                    HarnessMetric("Overall", diagnosticsState.healthStatus, Modifier.weight(1f))
                    HarnessMetric("Checks", diagnosticsState.health.size.toString(), Modifier.weight(1f))
                }
                Text(
                    if (diagnosticsState.health.isEmpty()) {
                        "Health checks have not been run in this process."
                    } else {
                        "Results are retained in the process-scoped telemetry repository."
                    },
                )
                HarnessPrimaryButton(
                    "Run health checks",
                    enabled = !isBusy() && !diagnosticActionRunning(),
                    onClick = ::runHealthChecks,
                )
            }
        }
        items(diagnosticsState.health, key = { it.id }) { health ->
            HarnessCard {
                Text(health.id, style = MaterialTheme.typography.titleMedium)
                Text(health.detail)
                HarnessMetricRow {
                    HarnessMetric("Status", health.status, Modifier.weight(1f))
                    HarnessMetric("Duration", health.duration, Modifier.weight(1f))
                }
            }
        }
    }

    private fun androidx.compose.foundation.lazy.LazyListScope.resourceDiagnostics() {
        val history = resourceSource.history()
        item {
            HarnessCard {
                Text("Device resources", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (history.snapshots.isEmpty()) {
                        "No resource snapshots have been captured in this process."
                    } else {
                        "${history.sampleCount} bounded snapshots retained. " +
                            "Unsupported values remain unavailable."
                    },
                )
                HarnessPrimaryButton(
                    "Capture resource snapshot",
                    enabled = !isBusy() && !diagnosticActionRunning(),
                    onClick = ::captureResourceSnapshot,
                )
            }
        }
        if (history.snapshots.isNotEmpty()) {
            item {
                HarnessCard {
                    Text("Resource history summary", style = MaterialTheme.typography.titleMedium)
                    HarnessMetricRow {
                        HarnessMetric("Current PSS", history.currentProcessPss, Modifier.weight(1f))
                        HarnessMetric("Trend", history.processPssTrend, Modifier.weight(1f))
                    }
                    HarnessMetricRow {
                        HarnessMetric("Minimum PSS", history.minimumProcessPss, Modifier.weight(1f))
                        HarnessMetric("Maximum PSS", history.maximumProcessPss, Modifier.weight(1f))
                    }
                    HarnessMetricRow {
                        HarnessMetric(
                            "Low-memory samples",
                            history.lowMemorySamples.toString(),
                            Modifier.weight(1f),
                        )
                        HarnessMetric("Thermal states", history.observedThermalStates, Modifier.weight(1f))
                    }
                }
            }
        }
        items(history.snapshots.take(RESOURCE_HISTORY_VISIBLE_LIMIT), key = { it.timestampEpochMs }) { resource ->
            HarnessCard {
                Text(
                    java.time.Instant.ofEpochMilli(resource.timestampEpochMs).toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
                HarnessMetricRow {
                    HarnessMetric("Process PSS", resource.processPss, Modifier.weight(1f))
                    HarnessMetric("Native heap", resource.nativeHeap, Modifier.weight(1f))
                }
                HarnessMetricRow {
                    HarnessMetric("Java heap", resource.javaHeap, Modifier.weight(1f))
                    HarnessMetric("Available", resource.availableMemory, Modifier.weight(1f))
                }
                HarnessMetricRow {
                    HarnessMetric("Low memory", resource.lowMemory, Modifier.weight(1f))
                    HarnessMetric("Thermal", resource.thermalStatus, Modifier.weight(1f))
                }
            }
        }
    }

    private fun androidx.compose.foundation.lazy.LazyListScope.benchmarkDiagnostics() {
        item {
            HarnessCard {
                Text("Benchmarks", style = MaterialTheme.typography.titleLarge)
                HarnessMetricRow {
                    HarnessMetric("Baselines", benchmarkState.baselines.size.toString(), Modifier.weight(1f))
                    HarnessMetric("Known keys", benchmarkState.readiness.size.toString(), Modifier.weight(1f))
                    HarnessMetric("History", benchmarkState.history.size.toString(), Modifier.weight(1f))
                }
                Text(
                    benchmarkState.sourceError
                        ?: benchmarkState.captureDetail
                        ?: "Readiness is evaluated independently for every app, use case, model and cold/warm key.",
                )
                HarnessPrimaryButton(
                    "Capture all ready baselines",
                    enabled = importedModel != null &&
                        benchmarkState.readiness.any { it.captureReady } &&
                        !isBusy() &&
                        !diagnosticActionRunning(),
                    onClick = ::captureBenchmarkBaselines,
                )
            }
        }
        items(benchmarkState.readiness, key = { "readiness:${it.stableId}" }) { readiness ->
            HarnessCard {
                Text(
                    "${readiness.useCase} · ${readiness.loadKind}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(readiness.detail)
                HarnessMetricRow {
                    HarnessMetric(
                        "Baseline samples",
                        "${readiness.baselineSamples}/${readiness.baselineRequired}",
                        Modifier.weight(1f),
                    )
                    HarnessMetric(
                        "Post-baseline",
                        "${readiness.comparisonSamples}/${readiness.comparisonRequired}",
                        Modifier.weight(1f),
                    )
                }
                HarnessMetricRow {
                    HarnessMetric(
                        "Baseline",
                        if (readiness.baselineCaptured) "Captured" else "Not captured",
                        Modifier.weight(1f),
                    )
                    HarnessMetric(
                        "Regression",
                        if (readiness.comparisonReady) "Ready" else "Not ready",
                        Modifier.weight(1f),
                    )
                }
                if (!readiness.baselineCaptured) {
                    HarnessSecondaryButton(
                        "Capture this baseline",
                        enabled = readiness.captureReady && !isBusy() && !diagnosticActionRunning(),
                    ) {
                        captureBenchmarkBaseline(readiness.stableId)
                    }
                }
            }
        }
        if (benchmarkState.history.isNotEmpty()) {
            item {
                Text("Retained baseline history", style = MaterialTheme.typography.titleLarge)
            }
        }
        items(benchmarkState.history, key = { "history:${it.stableId}" }) { history ->
            HarnessCard {
                Text(
                    "${history.useCase} · ${history.loadKind}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(history.capturedAt)
                HarnessMetricRow {
                    HarnessMetric("State", if (history.active) "Active" else "Historical", Modifier.weight(1f))
                    HarnessMetric("Samples", history.samples, Modifier.weight(1f))
                }
                HarnessMetricRow {
                    HarnessMetric("Median TTFT", history.medianTtft, Modifier.weight(1f))
                    HarnessMetric("p95 total", history.p95Total, Modifier.weight(1f))
                }
                HarnessMetric("Median decode", history.medianDecode)
            }
        }

        items(benchmarkState.baselines, key = { "baseline:${it.stableId}" }) { benchmark ->
            HarnessCard {
                Text(
                    "${benchmark.useCase} · ${benchmark.loadKind}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(benchmark.regressionDetail)
                HarnessMetricRow {
                    HarnessMetric("Samples", benchmark.samples, Modifier.weight(1f))
                    HarnessMetric("Regression", benchmark.regressionStatus, Modifier.weight(1f))
                }
                HarnessMetricRow {
                    HarnessMetric("Median TTFT", benchmark.medianTtft, Modifier.weight(1f))
                    HarnessMetric("p95 total", benchmark.p95Total, Modifier.weight(1f))
                }
                HarnessMetric("Median decode", benchmark.medianDecode)
            }
        }
    }

    private fun androidx.compose.foundation.lazy.LazyListScope.runDiagnostics() {
        item {
            HarnessCard {
                Text("Generation runs", style = MaterialTheme.typography.titleLarge)
                when {
                    diagnosticsState.sourceError != null -> Text(requireNotNull(diagnosticsState.sourceError))

                    diagnosticsState.runs.isEmpty() -> {
                        Text("No telemetry runs yet. Run a local prompt or validation to populate this view.")
                    }

                    else -> {
                        Text("${diagnosticsState.runs.size} privacy-safe run records retained in this process.")
                    }
                }
            }
        }
        items(diagnosticsState.runs, key = { it.requestId }) { run ->
            HarnessCard {
                Text(run.status, style = MaterialTheme.typography.titleMedium)
                Text(run.useCase, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HarnessMetricRow {
                    HarnessMetric("Load", run.modelLoadKind, Modifier.weight(1f))
                    HarnessMetric("TTFT", run.timeToFirstToken, Modifier.weight(1f))
                }
                HarnessMetricRow {
                    HarnessMetric("Total", run.totalDuration, Modifier.weight(1f))
                    HarnessMetric("Decode", run.throughput, Modifier.weight(1f))
                }
                HarnessMetric("Model", run.modelDigestPrefix + "…")
                HarnessMetric("Request", run.requestId.take(12) + "…")
                HarnessSecondaryButton("View request timeline") {
                    openRequestTimeline(run.requestId)
                }
            }
        }
    }

    private fun androidx.compose.foundation.lazy.LazyListScope.validationDiagnostics() {
        item {
            HarnessCard {
                Text("Physical-device validation", style = MaterialTheme.typography.titleLarge)
                Text("Runs generation, cancellation and repeated load/generate/unload memory cycles.")
                HarnessPrimaryButton(
                    "Run full validation",
                    enabled = importedModel != null && !isBusy() && !diagnosticActionRunning(),
                ) {
                    afterPlaygroundRuntimeReleased {
                        latestReport = ""
                        operationStatus = "Starting validation…"
                        controller.runFullValidation()
                    }
                }
            }
        }
        item {
            HarnessCard {
                Text("Privacy-safe report", style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text(
                        latestReport.ifBlank { "No validation report yet." },
                        fontFamily = FontFamily.Monospace,
                    )
                }
                HarnessSecondaryButton(
                    "Copy report",
                    enabled = latestReport.isNotBlank(),
                    onClick = ::copyReport,
                )
                HarnessSecondaryButton(
                    "Share report",
                    enabled = latestReport.isNotBlank(),
                    onClick = ::shareReport,
                )
            }
        }
    }

    @Composable
    private fun SettingsScreen(onBack: () -> Unit) {
        ScreenList("Settings") {
            item {
                HarnessCard {
                    Text("Privacy", style = MaterialTheme.typography.titleLarge)
                    Text("Inference, prompts, generated output and GGUF artifacts remain on this device.")
                    Text(
                        "Runs, health, resources, benchmark baselines and logs are bounded and " +
                            "process-memory-only; prompt and output content are excluded.",
                    )
                }
            }
            item {
                HarnessCard {
                    Text("Build", style = MaterialTheme.typography.titleLarge)
                    HarnessMetric("Application", "Harness 0.4.0")
                    HarnessMetric("Format", "GGUF only")
                    HarnessMetric("Transport", "In-process")
                    HarnessMetric("Telemetry", "In-memory")
                }
            }
            item { HarnessSecondaryButton("Back to overview", onClick = onBack) }
        }
    }

    @Composable
    private fun ScreenList(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text(title, style = MaterialTheme.typography.headlineLarge) }
            content()
        }
    }

    private fun startPlayground(prompt: String, maxTokens: String, temperature: String, seed: String) {
        val model = importedModel ?: return
        val options = runCatching {
            PlaygroundRequestOptions.parse(maxTokens, temperature, seed)
        }.getOrElse {
            Toast.makeText(this, "Invalid generation settings", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { playgroundController.start(model, prompt, options) }
            .onFailure {
                Toast.makeText(this, "Unable to start local inference", Toast.LENGTH_LONG).show()
            }
    }

    private fun afterPlaygroundRuntimeReleased(action: () -> Unit) {
        if (!playgroundController.releaseRuntime { runOnUiThread(action) }) {
            Toast.makeText(this, "Cancel or wait for the active generation", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateKeepScreenOn() {
        if (controllerBusy || modelDistributionState.operationActive || playgroundState.active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun diagnosticActionRunning(): Boolean = healthRunning || resourceCaptureRunning || benchmarkCaptureRunning

    private fun isBusy(): Boolean = controllerBusy || modelDistributionState.operationActive || playgroundController.active

    private fun copyLog(log: DiagnosticsLogUi) {
        copyToClipboard("Harness log entry", log.copyText())
        Toast.makeText(this, "Log entry copied", Toast.LENGTH_SHORT).show()
    }

    private fun copyReport() {
        copyToClipboard("Harness validation", latestReport)
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun shareReport() {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Harness physical-device validation")
                    putExtra(Intent.EXTRA_TEXT, latestReport)
                },
                "Share validation report",
            ),
        )
    }

    private fun formatBytes(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)

    private enum class HarnessDestination(val label: String, val shortLabel: String) {
        OVERVIEW("Overview", "O"),
        PLAYGROUND("Playground", "P"),
        MODELS("Models", "M"),
        DIAGNOSTICS("Diagnostics", "D"),
        SETTINGS("Settings", "S"),
        ;

        companion object {
            val main = listOf(OVERVIEW, PLAYGROUND, MODELS, DIAGNOSTICS)
        }
    }

    private companion object {
        const val STATE_REPORT = "report"
        const val RESOURCE_HISTORY_VISIBLE_LIMIT = 10
        const val DEFAULT_ARCHITECTURE = "qwen3"
        const val DEFAULT_QUANTIZATION = "Q4_K_M"
        const val DEFAULT_MAX_OUTPUT_TOKENS = "128"
        const val DEFAULT_TEMPERATURE = "0.2"
        const val DEFAULT_SEED = "42"
        const val DEFAULT_PROMPT = "Explain in two sentences why local inference improves privacy."
        val MODEL_MIME_TYPES = arrayOf("application/octet-stream", "application/gguf", "application/x-gguf")
    }
}
