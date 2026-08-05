#!/usr/bin/env python3
"""Apply the connected catalog/download/install wiring to the phone-test app."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/MainActivity.kt"
CONTROLLER = ROOT / "apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/PhoneTestController.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one {label} match, found {count}")
    return text.replace(old, new, 1)


def patch_main_activity() -> None:
    text = MAIN.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "    private lateinit var controller: PhoneTestController\n"
        "    private lateinit var playgroundController: PhonePlaygroundController\n",
        "    private lateinit var controller: PhoneTestController\n"
        "    private lateinit var modelDistributionController: PhoneModelDistributionController\n"
        "    private lateinit var playgroundController: PhonePlaygroundController\n",
        "controller field",
    )
    text = replace_once(
        text,
        "    private var importedModel by mutableStateOf<ImportedPhoneModel?>(null)\n"
        "    private var latestReport by mutableStateOf(\"\")\n",
        "    private var importedModel by mutableStateOf<ImportedPhoneModel?>(null)\n"
        "    private var modelDistributionState by mutableStateOf(PhoneModelDistributionState())\n"
        "    private var latestReport by mutableStateOf(\"\")\n",
        "distribution state",
    )
    text = replace_once(
        text,
        "        controller = PhoneTestController(this, this)\n"
        "        playgroundController = PhonePlaygroundController(this, ::onPlaygroundStateChanged)\n",
        "        controller = PhoneTestController(this, this)\n"
        "        modelDistributionController = PhoneModelDistributionController.from(\n"
        "            context = this,\n"
        "            runtimeGraph = runtimeGraph,\n"
        "            listener = PhoneModelDistributionListener { state ->\n"
        "                runOnUiThread {\n"
        "                    modelDistributionState = state\n"
        "                    operationStatus = state.message\n"
        "                    updateKeepScreenOn()\n"
        "                }\n"
        "            },\n"
        "        )\n"
        "        playgroundController = PhonePlaygroundController(this, ::onPlaygroundStateChanged)\n",
        "controller initialization",
    )
    text = replace_once(
        text,
        "        diagnosticsExecutor.shutdownNow()\n"
        "        playgroundController.close()\n"
        "        controller.close()\n",
        "        diagnosticsExecutor.shutdownNow()\n"
        "        modelDistributionController.close()\n"
        "        playgroundController.close()\n"
        "        controller.close()\n",
        "controller close",
    )
    text = replace_once(
        text,
        "        runOnUiThread {\n"
        "            importedModel = model\n"
        "            refreshDiagnostics()\n"
        "        }\n"
        "    }\n\n"
        "    override fun onReport(report: String) {",
        "        runOnUiThread {\n"
        "            importedModel = model\n"
        "            if (::modelDistributionController.isInitialized) {\n"
        "                modelDistributionController.refresh()\n"
        "            }\n"
        "            refreshDiagnostics()\n"
        "        }\n"
        "    }\n\n"
        "    override fun onReport(report: String) {",
        "model refresh callback",
    )
    old_models = '''    @Composable
    private fun ModelsScreen(onImport: () -> Unit) {
        ScreenList("Models") {
            item { HarnessPrimaryButton("Import GGUF", enabled = !isBusy(), onClick = onImport) }
            item {
                val model = importedModel
                if (model == null) {
                    HarnessCard {
                        Text("No imported models", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Harness currently exposes the selected model while the durable " +
                                "multi-model catalog is introduced.",
                        )
                    }
                } else {
                    HarnessCard {
                        Text(model.fileName, style = MaterialTheme.typography.titleLarge)
                        HarnessMetricRow {
                            HarnessMetric("Architecture", model.architecture, Modifier.weight(1f))
                            HarnessMetric("Quantization", model.quantization, Modifier.weight(1f))
                        }
                        HarnessMetric("SHA-256", model.digest.sha256.take(24) + "…")
                        HarnessMetric("Size", formatBytes(model.sizeBytes))
                        HarnessSecondaryButton("Remove model", enabled = !isBusy()) {
                            afterPlaygroundRuntimeReleased { controller.removeModel() }
                        }
                    }
                }
            }
        }
    }
'''
    new_models = '''    @Composable
    private fun ModelsScreen(onImport: () -> Unit) {
        ScreenList("Models") {
            item {
                PhoneModelDistributionCatalog(
                    state = modelDistributionState,
                    onDownload = modelDistributionController::download,
                    onCancel = modelDistributionController::cancelDownload,
                    onInstall = modelDistributionController::install,
                    onSelectInstalled = { metadata ->
                        afterPlaygroundRuntimeReleased {
                            controller.selectInstalledModel(metadata.asImportedPhoneModel())
                        }
                    },
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
                        HarnessSecondaryButton("Remove model", enabled = !isBusy()) {
                            afterPlaygroundRuntimeReleased { controller.removeModel() }
                        }
                    }
                }
            }
        }
    }
'''
    text = replace_once(text, old_models, new_models, "ModelsScreen")
    text = replace_once(
        text,
        "                    HarnessMetric(\"Application\", \"Harness 0.3.0\")\n",
        "                    HarnessMetric(\"Application\", \"Harness 0.4.0\")\n",
        "build version",
    )
    text = replace_once(
        text,
        "        if (controllerBusy || playgroundState.active) {\n",
        "        if (controllerBusy || modelDistributionState.operationActive || playgroundState.active) {\n",
        "keep screen on",
    )
    text = replace_once(
        text,
        "    private fun isBusy(): Boolean = controllerBusy || playgroundController.active\n",
        "    private fun isBusy(): Boolean =\n"
        "        controllerBusy || modelDistributionState.operationActive || playgroundController.active\n",
        "busy state",
    )
    MAIN.write_text(text, encoding="utf-8")


def patch_phone_test_controller() -> None:
    text = CONTROLLER.read_text(encoding="utf-8")
    marker = '''    fun removeModel() {
        runExclusive {
'''
    selection = '''    fun selectInstalledModel(model: ImportedPhoneModel) {
        runExclusive {
            progress("Verifying installed model before selection")
            val stored = requireNotNull(modelStore.find(model.digest)) {
                "Installed model is no longer available"
            }
            check(stored.verified) { "Installed model is not marked as verified" }
            val verification = modelStore.verify(model.digest)
            check(verification.valid) { verification.detail }
            persist(model)
            currentModel = model
            post { listener.onModelChanged(model) }
            progress("${model.fileName} selected for local inference")
        }
    }

    fun removeModel() {
        runExclusive {
'''
    text = replace_once(text, marker, selection, "installed model selection")
    CONTROLLER.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_main_activity()
    patch_phone_test_controller()
