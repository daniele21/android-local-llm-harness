#!/usr/bin/env python3
"""Apply the final structural Detekt fixes to the model-management recovery."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    occurrences = text.count(old)
    if occurrences != 1:
        raise RuntimeError(f"Expected one anchor in {path}, found {occurrences}")
    path.write_text(text.replace(old, new, 1))


def update_main_activity() -> None:
    path = SOURCE / "MainActivity.kt"
    replace_once(
        path,
        "protectedModelDigest = runtimeGraph::loadedModelDigest,",
        "protectedModelDigest = { runtimeGraph.loadedModelDigest },",
    )
    replace_once(
        path,
        """            importedModel = model
            if (::modelDistributionController.isInitialized) {
""",
        """            importedModel = model
            selectedRemovalConfirmationPending = false
            if (::modelDistributionController.isInitialized) {
""",
    )
    replace_once(
        path,
        """                PhoneModelDistributionCatalog(
                    state = modelDistributionState,
                    onDownload = modelDistributionController::download,
                    onCancel = modelDistributionController::cancelDownload,
                    onInstall = modelDistributionController::install,
                    onVerifyInstalled = modelDistributionController::verifyInstalled,
                    onRequestRemove = modelDistributionController::requestRemove,
                    onCancelRemove = modelDistributionController::cancelRemove,
                    onConfirmRemove = modelDistributionController::confirmRemove,
                    onSelectInstalled = { metadata ->
                        afterPlaygroundRuntimeReleased {
                            controller.selectInstalledModel(metadata.asImportedPhoneModel())
                        }
                    },
                )
""",
        """                PhoneModelDistributionCatalog(
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
""",
    )
    replace_once(
        path,
        """                        HarnessSecondaryButton("Remove model", enabled = !isBusy()) {
                            afterPlaygroundRuntimeReleased { controller.removeModel() }
                        }
""",
        """                        HarnessSecondaryButton(
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
""",
    )


def update_distribution_controller() -> None:
    path = SOURCE / "PhoneModelDistributionController.kt"
    replace_once(
        path,
        "protectedModelDigest = runtimeGraph::loadedModelDigest,",
        "protectedModelDigest = { runtimeGraph.loadedModelDigest },",
    )
    replace_once(
        path,
        """            detail =
            managementDetails[stableId] ?: runtime?.detail,
""",
        """            detail = managementDetails[stableId] ?: runtime?.detail,
""",
    )


def main() -> int:
    update_main_activity()
    update_distribution_controller()
    print("PR #53 structural Detekt fixes applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
