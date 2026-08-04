package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ModelDigest
import java.util.Locale

@Suppress("TooManyFunctions")
class ConsoleInventoryPresenter {
    fun models(snapshot: ConsoleSnapshot): ConsoleScreen {
        val inventory = snapshot.modelInventory
        val cards = mutableListOf<ConsoleCard>()
        inventory.sourceError?.let { error ->
            cards += sourceErrorCard("Model inventory source", error)
        }
        snapshot.modelControl.sourceError?.let { error ->
            cards += sourceErrorCard("Model management source", error)
        }
        snapshot.modelControl.lastOperation?.let { outcome -> cards += operationCard(outcome) }
        cards += inventorySummary(snapshot)

        when {
            !inventory.available -> cards += emptyCard("Model inventory is not connected")
            inventory.entries.isEmpty() -> cards += emptyCard("No models installed in this source")
            else -> cards += inventory.entries.map { model -> modelCard(model, snapshot) }
        }

        return ConsoleScreen(
            title = "Installed models",
            subtitle = "Content-addressed inventory with explicit import, verification and removal",
            cards = cards,
            actions = modelActions(snapshot),
        )
    }

    fun runtime(snapshot: ConsoleSnapshot): ConsoleScreen = ConsoleScreen(
        title = "Active runtime",
        subtitle = "Read-only runtime lifecycle and scheduler state",
        cards = listOf(
            runtimeCard(snapshot.runtime),
            schedulerCard(snapshot.runtime),
            ConsoleCard(
                title = "Runtime contract boundary",
                lines = listOf(
                    "Session descriptors: Not exposed by RuntimeSnapshot",
                    "Context parameters: Not exposed by RuntimeSnapshot",
                    "Active request identity: Not exposed by RuntimeSnapshot",
                    "Controls: Read-only in this slice",
                ),
                emphasis = ConsoleEmphasis.NEUTRAL,
            ),
        ),
    )

    fun runtimeCard(runtime: ConsoleRuntimeState): ConsoleCard = ConsoleCard(
        title = "Runtime connection",
        lines = listOf(
            "Connection: ${if (runtime.connected) "Connected" else "Not connected"}",
            "State: ${runtime.status}",
            "Backend: ${runtime.backend}",
            "Loaded model: ${runtime.loadedModel}",
            "Source: ${runtime.source}",
        ),
        emphasis = when {
            !runtime.connected -> ConsoleEmphasis.WARNING
            runtime.status == "FAILED" -> ConsoleEmphasis.NEGATIVE
            runtime.status == "READY" || runtime.status == "GENERATING" -> ConsoleEmphasis.POSITIVE
            else -> ConsoleEmphasis.NEUTRAL
        },
    )

    fun inventorySummary(snapshot: ConsoleSnapshot): ConsoleCard {
        val inventory = snapshot.modelInventory
        val control = snapshot.modelControl
        return ConsoleCard(
            title = "Model inventory",
            lines = listOf(
                "Availability: ${if (inventory.available) "Available" else "Not connected"}",
                "Models: ${inventory.modelCount}",
                "Stored size: ${formatBytes(inventory.totalBytes)}",
                "Active model: ${snapshot.runtime.loadedModel}",
                "Inventory source: ${inventory.source}",
                "Management: ${if (control.available) "Available" else "Not connected"}",
                "Management source: ${control.source}",
                "Operation running: ${control.executionInProgress}",
            ),
            emphasis = when {
                inventory.sourceError != null || control.sourceError != null -> ConsoleEmphasis.NEGATIVE
                !inventory.available || !control.available -> ConsoleEmphasis.WARNING
                else -> ConsoleEmphasis.NEUTRAL
            },
        )
    }

    private fun modelActions(snapshot: ConsoleSnapshot): List<ConsoleAction> {
        val control = snapshot.modelControl
        if (!control.available) return emptyList()
        val enabled = !control.executionInProgress
        val actions = mutableListOf<ConsoleAction>()
        if (control.importAvailable) {
            actions += ConsoleAction(
                type = ConsoleActionType.IMPORT_MODEL,
                label = "Select and import GGUF",
                enabled = enabled,
            )
        }
        snapshot.modelInventory.entries.forEach { model ->
            if (control.verifyAvailable) {
                actions += ConsoleAction(
                    type = ConsoleActionType.VERIFY_MODEL,
                    label = "Verify ${shortDigest(model.digest.sha256)}",
                    modelDigest = model.digest,
                    enabled = enabled,
                )
            }
            if (control.removeAvailable) {
                val active = isActive(model.digest, snapshot.runtime.loadedModel)
                actions += ConsoleAction(
                    type = ConsoleActionType.REMOVE_MODEL,
                    label = if (active) {
                        "Cannot remove loaded ${shortDigest(model.digest.sha256)}"
                    } else {
                        "Remove ${shortDigest(model.digest.sha256)}"
                    },
                    modelDigest = model.digest,
                    enabled = enabled && !active,
                )
            }
        }
        return actions
    }

    private fun schedulerCard(runtime: ConsoleRuntimeState): ConsoleCard = ConsoleCard(
        title = "Sessions and queue",
        lines = listOf(
            "Active sessions: ${runtime.activeSessions ?: "Unavailable"}",
            "Queued requests: ${runtime.queueDepth ?: "Unavailable"}",
            "Decode policy: Single active decode",
        ),
        emphasis = if (runtime.connected) ConsoleEmphasis.NEUTRAL else ConsoleEmphasis.WARNING,
    )

    private fun modelCard(model: ConsoleInstalledModel, snapshot: ConsoleSnapshot): ConsoleCard {
        val active = isActive(model.digest, snapshot.runtime.loadedModel)
        val latestVerification = snapshot.modelControl.lastOperation
            ?.takeIf { it.operation == ConsoleModelOperation.VERIFY && it.digest == model.digest }
        return ConsoleCard(
            title = if (active) {
                "ACTIVE · ${shortDigest(model.digest.sha256)}"
            } else {
                shortDigest(model.digest.sha256)
            },
            lines = listOf(
                "Digest: ${model.digest.sha256}",
                "Size: ${formatBytes(model.sizeBytes)}",
                "Snapshot integrity: ${model.integrity.label}",
                "Latest explicit verification: ${verificationLabel(latestVerification)}",
                "Runtime role: ${if (active) "Loaded" else "Installed"}",
                "Removal: ${if (active) "Blocked while loaded" else "Available when management is connected"}",
            ),
            emphasis = when {
                active -> ConsoleEmphasis.POSITIVE
                latestVerification?.success == true -> ConsoleEmphasis.POSITIVE
                latestVerification?.success == false -> ConsoleEmphasis.NEGATIVE
                model.integrity == ConsoleModelIntegrity.VERIFIED -> ConsoleEmphasis.POSITIVE
                else -> ConsoleEmphasis.NEUTRAL
            },
        )
    }

    private fun operationCard(outcome: ConsoleModelOperationOutcome): ConsoleCard = ConsoleCard(
        title = "Latest model operation · ${outcome.operation.name}",
        lines = listOf(
            "Result: ${if (outcome.success) "Succeeded" else "Did not complete"}",
            "Model: ${outcome.digest?.sha256 ?: "Unavailable"}",
            "Detail: ${outcome.detail}",
        ),
        emphasis = when {
            outcome.success -> ConsoleEmphasis.POSITIVE
            outcome.sourceError != null -> ConsoleEmphasis.NEGATIVE
            else -> ConsoleEmphasis.WARNING
        },
    )

    private fun sourceErrorCard(title: String, error: String): ConsoleCard = ConsoleCard(
        title = title,
        lines = listOf(error),
        emphasis = ConsoleEmphasis.NEGATIVE,
    )

    private fun emptyCard(message: String): ConsoleCard = ConsoleCard(
        title = "Installed models",
        lines = listOf(message),
        emphasis = ConsoleEmphasis.WARNING,
    )

    private fun verificationLabel(outcome: ConsoleModelOperationOutcome?): String = when {
        outcome == null -> "Not run in this console session"
        outcome.success -> "Passed"
        else -> "Failed"
    }

    private fun isActive(digest: ModelDigest, loadedModel: String): Boolean =
        digest.sha256.equals(loadedModel, ignoreCase = true)

    private fun formatBytes(value: Long): String {
        val mib = value.toDouble() / BYTES_PER_MIB
        return String.format(Locale.US, "%.1f MiB", mib)
    }

    private fun shortDigest(value: String): String = value.take(SHORT_DIGEST_LENGTH)

    private val ConsoleModelIntegrity.label: String
        get() = when (this) {
            ConsoleModelIntegrity.VERIFIED -> "Verified"
            ConsoleModelIntegrity.NOT_CHECKED -> "Not checked"
        }

    private companion object {
        const val BYTES_PER_MIB = 1024.0 * 1024.0
        const val SHORT_DIGEST_LENGTH = 16
    }
}
