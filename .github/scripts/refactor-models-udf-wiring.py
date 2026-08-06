from pathlib import Path

MAIN = Path("apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/MainActivity.kt")


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
    "harnessViewModel.attachModelEffects(modelEffects)",
    "harnessViewModel.models.attach(modelEffects)",
    "attach Models coordinator",
)
main = replace_once(
    main,
    "harnessViewModel.detachModelEffects(modelEffects)",
    "harnessViewModel.models.detach(modelEffects)",
    "detach Models coordinator",
)

models_region = '''    @Composable
    private fun ModelsScreen(state: HarnessUiState) {
        val inventory = state.modelInventory
        ScreenList(title = null) {
            item { ModelsHeader(state.busy) }
            item { ModelsSummaryCard(inventory) }
            if (inventory.degradedCount > 0) {
                item { ModelsRecoveryCard(inventory) }
            }
            item { SelectedModelCard(state, inventory.selectedItem) }
            item { ModelCatalogHeader(state.modelDistribution) }
            item { ModelCatalogContent(state.modelDistribution) }
        }
    }

    @Composable
    private fun ModelsHeader(busy: Boolean) {
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
                enabled = !busy,
                modifier = Modifier,
                onClick = { harnessViewModel.models.requestImport() },
            )
        }
    }

    @Composable
    private fun ModelsSummaryCard(inventory: HarnessModelInventoryState) {
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

    @Composable
    private fun ModelsRecoveryCard(inventory: HarnessModelInventoryState) {
        HarnessCard {
            HarnessStatusBadge("RECOVERY REQUIRED", HarnessStatusTone.WARNING)
            Text("Runtime and model selection are not aligned.")
            inventory.items
                .filter { it.lifecycle == HarnessModelLifecycle.DEGRADED }
                .forEach { item ->
                    Text(
                        item.detail ?: item.degradation?.name.orEmpty().replace('_', ' '),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            HarnessSecondaryButton("Refresh model state") {
                harnessViewModel.models.executeCatalog(ModelCatalogCommand.Refresh)
            }
        }
    }

    @Composable
    private fun SelectedModelCard(
        state: HarnessUiState,
        selected: HarnessModelInventoryItem?,
    ) {
        if (selected == null) {
            EmptySelectedModelCard()
        } else {
            ActiveSelectedModelCard(state, selected)
        }
    }

    @Composable
    private fun EmptySelectedModelCard() {
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
                Text(
                    "Not loaded",
                    style = MaterialTheme.typography.labelLarge,
                    color = HarnessColors.Warning,
                )
            }
        }
    }

    @Composable
    private fun ActiveSelectedModelCard(
        state: HarnessUiState,
        selected: HarnessModelInventoryItem,
    ) {
        HarnessCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ACTIVE MODEL", style = MaterialTheme.typography.labelLarge)
                HarnessStatusBadge(
                    selected.lifecycle.name.replace('_', ' '),
                    selected.lifecycle.statusTone(),
                )
            }
            Text(selected.displayName, style = MaterialTheme.typography.titleLarge)
            HarnessMetricRow {
                HarnessMetric(
                    "Architecture",
                    selected.architecture ?: "Unavailable",
                    Modifier.weight(1f),
                )
                HarnessMetric(
                    "Quantization",
                    selected.quantization ?: "Unavailable",
                    Modifier.weight(1f),
                )
            }
            Text(
                "${selected.sizeBytes?.let(::formatBytes) ?: "Unavailable"} · " +
                    (selected.digest?.take(12)?.plus("…") ?: "Digest unavailable"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectedModelActions(state)
            SelectedModelRemoval(state)
        }
    }

    @Composable
    private fun SelectedModelActions(state: HarnessUiState) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HarnessSecondaryButton(
                text = "Verify",
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
                onClick = { harnessViewModel.models.verifySelected() },
            )
            HarnessSecondaryButton(
                text = "Import another",
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
                onClick = { harnessViewModel.models.requestImport() },
            )
        }
    }

    @Composable
    private fun SelectedModelRemoval(state: HarnessUiState) {
        if (state.removalConfirmationPending) {
            Text(
                "Removal permanently deletes the app-private model copy.",
                color = MaterialTheme.colorScheme.error,
            )
            HarnessPrimaryButton(
                "Confirm removal",
                enabled = !state.busy,
            ) {
                harnessViewModel.models.confirmSelectedRemoval()
            }
            HarnessSecondaryButton("Cancel removal") {
                harnessViewModel.models.cancelSelectedRemoval()
            }
        } else {
            HarnessSecondaryButton("Remove active model", enabled = !state.busy) {
                harnessViewModel.models.requestSelectedRemoval()
            }
        }
    }

    @Composable
    private fun ModelCatalogHeader(distribution: PhoneModelDistributionState) {
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
                label = distribution.catalogStatus.name.replace('_', ' '),
                tone = distribution.catalogStatus.statusTone(),
            )
        }
    }

    @Composable
    private fun ModelCatalogContent(distribution: PhoneModelDistributionState) {
        PhoneModelDistributionCatalog(
            state = distribution,
            actions = PhoneModelDistributionActions(
                download = {
                    harnessViewModel.models.executeCatalog(ModelCatalogCommand.Download(it))
                },
                cancelDownload = {
                    harnessViewModel.models.executeCatalog(ModelCatalogCommand.CancelDownload(it))
                },
                install = {
                    harnessViewModel.models.executeCatalog(ModelCatalogCommand.Install(it))
                },
                verifyInstalled = {
                    harnessViewModel.models.executeCatalog(ModelCatalogCommand.VerifyInstalled(it))
                },
                requestRemove = {
                    harnessViewModel.models.executeCatalog(ModelCatalogCommand.RequestRemoval(it))
                },
                cancelRemove = {
                    harnessViewModel.models.executeCatalog(ModelCatalogCommand.CancelRemoval(it))
                },
                confirmRemove = {
                    harnessViewModel.models.executeCatalog(ModelCatalogCommand.ConfirmRemoval(it))
                },
                selectInstalled = { harnessViewModel.models.selectInstalled(it) },
            ),
        )
    }

    private fun HarnessModelLifecycle.statusTone(): HarnessStatusTone = when (this) {
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
    }

    private fun PhoneCatalogLoadStatus.statusTone(): HarnessStatusTone =
        if (this == PhoneCatalogLoadStatus.READY) {
            HarnessStatusTone.SUCCESS
        } else {
            HarnessStatusTone.WARNING
        }

'''
main = replace_region(
    main,
    "    @Composable\n    private fun ModelsScreen",
    "    @Composable\n    private fun DiagnosticsScreen",
    models_region,
    "split Models UI",
)

effects_region = '''    private fun createModelEffects(onImport: () -> Unit): ModelEffects = object : ModelEffects {
        override fun snapshot(): ModelEffectsSnapshot = ModelEffectsSnapshot(
            distribution = modelDistributionController.snapshot(),
            selectedModel = controller.snapshotModel(),
            loadedDigest = runtimeGraph.loadedModelDigest?.sha256,
        )

        override fun requestImport(): Boolean = modelEffect(onImport)

        override fun executeCatalog(command: ModelCatalogCommand): Boolean = modelEffect {
            when (command) {
                ModelCatalogCommand.Refresh -> modelDistributionController.refresh()
                is ModelCatalogCommand.Download -> modelDistributionController.download(command.stableId)
                is ModelCatalogCommand.CancelDownload -> modelDistributionController.cancelDownload(command.stableId)
                is ModelCatalogCommand.Install -> modelDistributionController.install(command.stableId)
                is ModelCatalogCommand.VerifyInstalled -> modelDistributionController.verifyInstalled(command.stableId)
                is ModelCatalogCommand.RequestRemoval -> modelDistributionController.requestRemove(command.stableId)
                is ModelCatalogCommand.CancelRemoval -> modelDistributionController.cancelRemove(command.stableId)
                is ModelCatalogCommand.ConfirmRemoval -> modelDistributionController.confirmRemove(command.stableId)
            }
        }

        override fun selectInstalled(metadata: InstalledCatalogModelMetadata): Boolean =
            afterPlaygroundRuntimeReleased {
                controller.selectInstalledModel(metadata.asImportedPhoneModel())
            }

        override fun verifySelected(): Boolean = verifySelectedModel()

        override fun removeSelected(): Boolean = afterPlaygroundRuntimeReleased(controller::removeModel)
    }

'''
main = replace_region(
    main,
    "    private fun createModelEffects",
    "    private fun importModelDocument",
    effects_region,
    "grouped ModelEffects adapter",
)

for stale in (
    "harnessViewModel.attachModelEffects",
    "harnessViewModel.detachModelEffects",
    "harnessViewModel.requestModelImport",
    "harnessViewModel.refreshModels",
    "harnessViewModel.downloadModel",
    "harnessViewModel.cancelModelDownload",
    "harnessViewModel.installModel",
    "harnessViewModel.verifyInstalledModel",
    "harnessViewModel.requestCatalogModelRemoval",
    "harnessViewModel.cancelCatalogModelRemoval",
    "harnessViewModel.confirmCatalogModelRemoval",
    "harnessViewModel.selectInstalledModel",
    "harnessViewModel.verifySelectedModel",
    "harnessViewModel.requestSelectedModelRemoval",
    "harnessViewModel.cancelSelectedModelRemoval",
    "harnessViewModel.confirmSelectedModelRemoval",
):
    if stale in main:
        raise RuntimeError(f"stale direct ViewModel Models action remains: {stale}")

MAIN.write_text(main)
