from pathlib import Path

MAIN = Path("apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/MainActivity.kt")
CONTROLLER = Path("apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/PhoneTestController.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def replace_region(text: str, start: str, end: str, replacement: str, label: str) -> str:
    start_index = text.find(start)
    if start_index < 0:
        raise RuntimeError(f"{label}: start marker not found")
    end_index = text.find(end, start_index + len(start))
    if end_index < 0:
        raise RuntimeError(f"{label}: end marker not found")
    return text[:start_index] + replacement + text[end_index:]


main = MAIN.read_text()
main = replace_once(
    main,
    "import android.content.Intent\n",
    "import android.content.Intent\nimport android.net.Uri\n",
    "Uri import",
)
main = replace_once(
    main,
    "import androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.saveable.rememberSaveable\n",
    "import androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.saveable.rememberSaveable\n",
    "remember import",
)
main = replace_once(
    main,
    "    private var importedModel by mutableStateOf<ImportedPhoneModel?>(null)\n"
    "    private var modelDistributionState by mutableStateOf(PhoneModelDistributionState())\n",
    "",
    "Activity model mirrors",
)
main = replace_once(
    main,
    "    private var selectedRemovalConfirmationPending by mutableStateOf(false)\n",
    "",
    "Activity removal mirror",
)
main = replace_once(
    main,
    "\n    @Volatile\n    private var selectedModelForDiagnostics: ImportedPhoneModel? = null\n",
    "",
    "diagnostics model mirror",
)
main = replace_once(
    main,
    "            selectedModel = { selectedModelForDiagnostics },\n",
    "            selectedModel = { harnessViewModel.uiState.value.importedModel },\n",
    "health selected model",
)
main = replace_once(
    main,
    "        benchmarkSource = HarnessBenchmarkSource(runtimeGraph.telemetryRepository) {\n"
    "            selectedModelForDiagnostics\n"
    "        }\n",
    "        benchmarkSource = HarnessBenchmarkSource(runtimeGraph.telemetryRepository) {\n"
    "            harnessViewModel.uiState.value.importedModel\n"
    "        }\n",
    "benchmark selected model",
)
main = replace_once(
    main,
    "                    modelDistributionState = state\n"
    "                    operationStatus = state.message\n",
    "                    operationStatus = state.message\n",
    "distribution listener mirror",
)
main = replace_once(
    main,
    "    override fun onProgress(message: String) {\n"
    "        runOnUiThread {\n"
    "            operationStatus = message\n"
    "            refreshDiagnostics()\n"
    "        }\n"
    "    }\n",
    "    override fun onProgress(message: String) {\n"
    "        runOnUiThread {\n"
    "            operationStatus = message\n"
    "            syncLoadedModelOwnership()\n"
    "            refreshDiagnostics()\n"
    "        }\n"
    "    }\n",
    "progress ownership sync",
)
main = replace_once(
    main,
    "    override fun onModelChanged(model: ImportedPhoneModel?) {\n"
    "        selectedModelForDiagnostics = model\n"
    "        runOnUiThread {\n"
    "            importedModel = model\n"
    "            harnessViewModel.dispatch(HarnessUiEvent.ModelChanged(model))\n"
    "            selectedRemovalConfirmationPending = false\n"
    "            if (::modelDistributionController.isInitialized) {\n"
    "                modelDistributionController.refresh()\n"
    "            }\n"
    "            refreshDiagnostics()\n"
    "        }\n"
    "    }\n",
    "    override fun onModelChanged(model: ImportedPhoneModel?) {\n"
    "        runOnUiThread {\n"
    "            harnessViewModel.dispatch(HarnessUiEvent.ModelChanged(model))\n"
    "            syncLoadedModelOwnership()\n"
    "            if (::modelDistributionController.isInitialized) {\n"
    "                modelDistributionController.refresh()\n"
    "            }\n"
    "            refreshDiagnostics()\n"
    "        }\n"
    "    }\n",
    "model listener UDF",
)
main = replace_once(
    main,
    "            harnessViewModel.dispatch(HarnessUiEvent.ReportChanged(report))\n"
    "            operationStatus = \"Validation completed\"\n",
    "            harnessViewModel.dispatch(HarnessUiEvent.ReportChanged(report))\n"
    "            syncLoadedModelOwnership()\n"
    "            operationStatus = \"Validation completed\"\n",
    "report ownership sync",
)
main = replace_once(
    main,
    "            harnessViewModel.dispatch(HarnessUiEvent.PlaygroundChanged(state))\n"
    "            updateKeepScreenOn()\n",
    "            harnessViewModel.dispatch(HarnessUiEvent.PlaygroundChanged(state))\n"
    "            syncLoadedModelOwnership()\n"
    "            updateKeepScreenOn()\n",
    "Playground ownership sync",
)
main = replace_once(
    main,
    "    private fun verifySelectedModel() {\n"
    "        val model = importedModel ?: return\n"
    "        if (isBusy()) return\n"
    "        operationStatus = \"Verifying selected model integrity…\"\n"
    "        diagnosticsExecutor.execute {\n"
    "            val outcome = selectedModelManagement.verify(model.digest)\n"
    "            runOnUiThread {\n"
    "                operationStatus = outcome.detail\n"
    "                modelDistributionController.refresh()\n"
    "                refreshDiagnostics()\n"
    "            }\n"
    "        }\n"
    "    }\n",
    "    private fun verifySelectedModel(): Boolean {\n"
    "        val model = harnessViewModel.uiState.value.importedModel ?: return false\n"
    "        if (isBusy()) return false\n"
    "        operationStatus = \"Verifying selected model integrity…\"\n"
    "        diagnosticsExecutor.execute {\n"
    "            val outcome = selectedModelManagement.verify(model.digest)\n"
    "            runOnUiThread {\n"
    "                operationStatus = outcome.detail\n"
    "                modelDistributionController.refresh()\n"
    "                refreshDiagnostics()\n"
    "            }\n"
    "        }\n"
    "        return true\n"
    "    }\n",
    "selected verification effect",
)
main = replace_once(
    main,
    "        val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->\n"
    "            if (uri != null) {\n"
    "                afterPlaygroundRuntimeReleased {\n"
    "                    controller.importModel(\n"
    "                        uri = uri,\n"
    "                        architecture = DEFAULT_ARCHITECTURE,\n"
    "                        quantization = DEFAULT_QUANTIZATION,\n"
    "                    )\n"
    "                }\n"
    "            }\n"
    "        }\n",
    "        val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->\n"
    "            if (uri != null) importModelDocument(uri)\n"
    "        }\n"
    "        val modelEffects = remember(modelPicker) {\n"
    "            createModelEffects { modelPicker.launch(MODEL_MIME_TYPES) }\n"
    "        }\n"
    "        DisposableEffect(modelEffects) {\n"
    "            harnessViewModel.attachModelEffects(modelEffects)\n"
    "            onDispose { harnessViewModel.detachModelEffects(modelEffects) }\n"
    "        }\n",
    "model effect attachment",
)
main = replace_once(
    main,
    "                        OverviewScreen(\n"
    "                            playground = uiState.playground,\n",
    "                        OverviewScreen(\n"
    "                            model = uiState.importedModel,\n"
    "                            playground = uiState.playground,\n",
    "overview model parameter",
)
main = replace_once(
    main,
    "                    composable(HarnessDestination.MODELS.route) {\n"
    "                        ModelsScreen(onImport = { modelPicker.launch(MODEL_MIME_TYPES) })\n"
    "                    }\n",
    "                    composable(HarnessDestination.MODELS.route) {\n"
    "                        ModelsScreen(state = uiState)\n"
    "                    }\n",
    "Models route state",
)
main = replace_once(
    main,
    "                        DiagnosticsScreen(\n"
    "                            onOpenRequestTimeline = { requestId ->\n",
    "                        DiagnosticsScreen(\n"
    "                            state = uiState,\n"
    "                            onOpenRequestTimeline = { requestId ->\n",
    "Diagnostics state parameter",
)
main = replace_once(
    main,
    "                        SettingsScreen(\n"
    "                            onOpenPrivacy = {\n",
    "                        SettingsScreen(\n"
    "                            model = uiState.importedModel,\n"
    "                            onOpenPrivacy = {\n",
    "Settings model parameter",
)
main = replace_once(
    main,
    "                            importedModel = importedModel,\n",
    "                            importedModel = uiState.importedModel,\n",
    "Storage detail model",
)
main = replace_once(
    main,
    "                            modelAvailable = importedModel != null,\n",
    "                            modelAvailable = uiState.importedModel != null,\n",
    "Physical validation model",
)

start = "    @Composable\n    private fun OverviewScreen(\n"
end = "    @Composable\n    private fun DeviceOnlyStatus"
start_index = main.find(start)
end_index = main.find(end, start_index)
if start_index < 0 or end_index < 0:
    raise RuntimeError("Overview region not found")
overview = main[start_index:end_index]
overview = replace_once(
    overview,
    "    private fun OverviewScreen(\n        playground: PlaygroundState,\n",
    "    private fun OverviewScreen(\n        model: ImportedPhoneModel?,\n        playground: PlaygroundState,\n",
    "Overview signature",
)
overview = overview.replace("importedModel", "model")
main = main[:start_index] + overview + main[end_index:]

models_screen = '''    @Composable
    private fun ModelsScreen(state: HarnessUiState) {
        val inventory = state.modelInventory
        val selected = inventory.selectedItem
        ScreenList(title = null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Models", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Manage your locally installed models",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HarnessPrimaryButton(
                        text = "+  Import model",
                        enabled = !state.busy,
                        modifier = Modifier,
                        onClick = { harnessViewModel.requestModelImport() },
                    )
                }
            }
            item {
                HarnessCard {
                    HarnessMetricRow {
                        HarnessMetric(
                            "Storage",
                            formatBytes(inventory.installedBytes),
                            Modifier.weight(1f),
                        )
                        HarnessMetric(
                            "Installed models",
                            inventory.installedCount.toString(),
                            Modifier.weight(1f),
                        )
                    }
                }
            }
            if (inventory.degradedCount > 0) {
                item {
                    HarnessCard {
                        HarnessStatusBadge("RECOVERY REQUIRED", HarnessStatusTone.WARNING)
                        Text("Runtime and model selection are not aligned.")
                        inventory.items.filter { it.lifecycle == HarnessModelLifecycle.DEGRADED }.forEach { item ->
                            Text(
                                item.detail ?: item.degradation?.name.orEmpty().replace('_', ' '),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HarnessSecondaryButton("Refresh model state") {
                            harnessViewModel.refreshModels()
                        }
                    }
                }
            }
            item {
                if (selected == null) {
                    HarnessCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HarnessDestinationIcon(
                                HarnessDestination.MODELS,
                                selected = true,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("No active model", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Import a GGUF or download a compatible catalog model",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("Not loaded", style = MaterialTheme.typography.labelLarge, color = HarnessColors.Warning)
                        }
                    }
                } else {
                    HarnessCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("ACTIVE MODEL", style = MaterialTheme.typography.labelLarge)
                            HarnessStatusBadge(
                                selected.lifecycle.name.replace('_', ' '),
                                when (selected.lifecycle) {
                                    HarnessModelLifecycle.SELECTED,
                                    HarnessModelLifecycle.LOADED,
                                    HarnessModelLifecycle.INSTALLED,
                                    -> HarnessStatusTone.SUCCESS

                                    HarnessModelLifecycle.DEGRADED,
                                    HarnessModelLifecycle.FAILED,
                                    HarnessModelLifecycle.INCOMPATIBLE,
                                    -> HarnessStatusTone.WARNING

                                    HarnessModelLifecycle.DOWNLOADING,
                                    HarnessModelLifecycle.INSTALLING,
                                    HarnessModelLifecycle.VERIFIED_READY_TO_INSTALL,
                                    -> HarnessStatusTone.INFO

                                    HarnessModelLifecycle.READY_TO_DOWNLOAD,
                                    HarnessModelLifecycle.CANCELLED,
                                    -> HarnessStatusTone.NEUTRAL
                                },
                            )
                        }
                        Text(selected.displayName, style = MaterialTheme.typography.titleLarge)
                        HarnessMetricRow {
                            HarnessMetric("Architecture", selected.architecture ?: "Unavailable", Modifier.weight(1f))
                            HarnessMetric("Quantization", selected.quantization ?: "Unavailable", Modifier.weight(1f))
                        }
                        Text(
                            "${selected.sizeBytes?.let(::formatBytes) ?: "Unavailable"} · " +
                                (selected.digest?.take(12)?.plus("…") ?: "Digest unavailable"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            HarnessSecondaryButton(
                                text = "Verify",
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                                onClick = { harnessViewModel.verifySelectedModel() },
                            )
                            HarnessSecondaryButton(
                                text = "Import another",
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                                onClick = { harnessViewModel.requestModelImport() },
                            )
                        }
                        if (state.removalConfirmationPending) {
                            Text(
                                "Removal permanently deletes the app-private model copy.",
                                color = MaterialTheme.colorScheme.error,
                            )
                            HarnessPrimaryButton(
                                "Confirm removal",
                                enabled = !state.busy,
                            ) {
                                harnessViewModel.confirmSelectedModelRemoval()
                            }
                            HarnessSecondaryButton("Cancel removal") {
                                harnessViewModel.cancelSelectedModelRemoval()
                            }
                        } else {
                            HarnessSecondaryButton("Remove active model", enabled = !state.busy) {
                                harnessViewModel.requestSelectedModelRemoval()
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Model catalog", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Compatible with this device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HarnessStatusBadge(
                        label = state.modelDistribution.catalogStatus.name.replace('_', ' '),
                        tone = if (state.modelDistribution.catalogStatus == PhoneCatalogLoadStatus.READY) {
                            HarnessStatusTone.SUCCESS
                        } else {
                            HarnessStatusTone.WARNING
                        },
                    )
                }
            }
            item {
                PhoneModelDistributionCatalog(
                    state = state.modelDistribution,
                    actions = PhoneModelDistributionActions(
                        download = { harnessViewModel.downloadModel(it) },
                        cancelDownload = { harnessViewModel.cancelModelDownload(it) },
                        install = { harnessViewModel.installModel(it) },
                        verifyInstalled = { harnessViewModel.verifyInstalledModel(it) },
                        requestRemove = { harnessViewModel.requestCatalogModelRemoval(it) },
                        cancelRemove = { harnessViewModel.cancelCatalogModelRemoval(it) },
                        confirmRemove = { harnessViewModel.confirmCatalogModelRemoval(it) },
                        selectInstalled = { harnessViewModel.selectInstalledModel(it) },
                    ),
                )
            }
        }
    }

'''
main = replace_region(
    main,
    "    @Composable\n    private fun ModelsScreen",
    "    @Composable\n    private fun DiagnosticsScreen",
    models_screen,
    "Models screen",
)
main = replace_once(
    main,
    "    private fun DiagnosticsScreen(onOpenRequestTimeline: (String) -> Unit) {\n",
    "    private fun DiagnosticsScreen(state: HarnessUiState, onOpenRequestTimeline: (String) -> Unit) {\n",
    "Diagnostics signature",
)
main = replace_once(
    main,
    "                    validationDiagnostics()\n",
    "                    validationDiagnostics(state)\n",
    "validation state call",
)
main = replace_once(
    main,
    "    private fun androidx.compose.foundation.lazy.LazyListScope.validationDiagnostics() {\n",
    "    private fun androidx.compose.foundation.lazy.LazyListScope.validationDiagnostics(state: HarnessUiState) {\n",
    "validation signature",
)
validation_start = main.find("    private fun androidx.compose.foundation.lazy.LazyListScope.validationDiagnostics")
validation_end = main.find("    @Composable\n    private fun SettingsScreen", validation_start)
if validation_start < 0 or validation_end < 0:
    raise RuntimeError("validation region not found")
validation = main[validation_start:validation_end].replace("importedModel != null", "state.importedModel != null")
main = main[:validation_start] + validation + main[validation_end:]
main = replace_once(
    main,
    "    private fun SettingsScreen(\n        onOpenPrivacy: () -> Unit,\n",
    "    private fun SettingsScreen(\n        model: ImportedPhoneModel?,\n        onOpenPrivacy: () -> Unit,\n",
    "Settings signature",
)
settings_start = main.find("    @Composable\n    private fun SettingsScreen")
settings_end = main.find("    @Composable\n    private fun SettingsSectionLabel", settings_start)
if settings_start < 0 or settings_end < 0:
    raise RuntimeError("Settings region not found")
settings = main[settings_start:settings_end].replace("importedModel", "model")
main = main[:settings_start] + settings + main[settings_end:]

model_effect_helpers = '''    private fun createModelEffects(onImport: () -> Unit): ModelEffects = object : ModelEffects {
        override fun snapshot(): ModelEffectsSnapshot = ModelEffectsSnapshot(
            distribution = modelDistributionController.snapshot(),
            selectedModel = controller.snapshotModel(),
            loadedDigest = runtimeGraph.loadedModelDigest?.sha256,
        )

        override fun requestImport(): Boolean = modelEffect(onImport)

        override fun refresh(): Boolean = modelEffect(modelDistributionController::refresh)

        override fun download(stableId: String): Boolean = modelEffect {
            modelDistributionController.download(stableId)
        }

        override fun cancelDownload(stableId: String): Boolean = modelEffect {
            modelDistributionController.cancelDownload(stableId)
        }

        override fun install(stableId: String): Boolean = modelEffect {
            modelDistributionController.install(stableId)
        }

        override fun verifyInstalled(stableId: String): Boolean = modelEffect {
            modelDistributionController.verifyInstalled(stableId)
        }

        override fun requestCatalogRemoval(stableId: String): Boolean = modelEffect {
            modelDistributionController.requestRemove(stableId)
        }

        override fun cancelCatalogRemoval(stableId: String): Boolean = modelEffect {
            modelDistributionController.cancelRemove(stableId)
        }

        override fun confirmCatalogRemoval(stableId: String): Boolean = modelEffect {
            modelDistributionController.confirmRemove(stableId)
        }

        override fun selectInstalled(metadata: InstalledCatalogModelMetadata): Boolean =
            afterPlaygroundRuntimeReleased {
                controller.selectInstalledModel(metadata.asImportedPhoneModel())
            }

        override fun verifySelected(): Boolean = verifySelectedModel()

        override fun removeSelected(): Boolean = afterPlaygroundRuntimeReleased(controller::removeModel)
    }

    private fun importModelDocument(uri: Uri) {
        afterPlaygroundRuntimeReleased {
            controller.importModel(
                uri = uri,
                architecture = DEFAULT_ARCHITECTURE,
                quantization = DEFAULT_QUANTIZATION,
            )
        }
    }

    private fun modelEffect(action: () -> Unit): Boolean = runCatching {
        action()
        true
    }.getOrDefault(false)

    private fun syncLoadedModelOwnership() {
        harnessViewModel.dispatch(
            HarnessUiEvent.LoadedModelChanged(runtimeGraph.loadedModelDigest?.sha256),
        )
    }

'''
main = replace_once(
    main,
    "    private fun startPlayground() {\n",
    model_effect_helpers + "    private fun startPlayground() {\n",
    "model effect helpers",
)
main = replace_once(
    main,
    "    private fun afterPlaygroundRuntimeReleased(action: () -> Unit) {\n"
    "        if (!harnessViewModel.releasePlaygroundRuntime { runOnUiThread(action) }) {\n"
    "            Toast.makeText(this, \"Cancel or wait for the active generation\", Toast.LENGTH_SHORT).show()\n"
    "        }\n"
    "    }\n",
    "    private fun afterPlaygroundRuntimeReleased(action: () -> Unit): Boolean {\n"
    "        val released = harnessViewModel.releasePlaygroundRuntime {\n"
    "            runOnUiThread {\n"
    "                syncLoadedModelOwnership()\n"
    "                action()\n"
    "            }\n"
    "        }\n"
    "        if (!released) {\n"
    "            Toast.makeText(this, \"Cancel or wait for the active generation\", Toast.LENGTH_SHORT).show()\n"
    "        }\n"
    "        return released\n"
    "    }\n",
    "runtime release result",
)

for forbidden in (
    "private var importedModel",
    "modelDistributionState",
    "selectedRemovalConfirmationPending",
    "selectedModelForDiagnostics",
):
    if forbidden in main:
        raise RuntimeError(f"stale Activity model state remains: {forbidden}")

MAIN.write_text(main)

controller = CONTROLLER.read_text()
controller = replace_once(
    controller,
    "    init {\n        post { listener.onModelChanged(currentModel) }\n    }\n\n    fun importModel",
    "    init {\n        post { listener.onModelChanged(currentModel) }\n    }\n\n"
    "    fun snapshotModel(): ImportedPhoneModel? = currentModel\n\n"
    "    fun importModel",
    "controller model snapshot",
)
CONTROLLER.write_text(controller)
