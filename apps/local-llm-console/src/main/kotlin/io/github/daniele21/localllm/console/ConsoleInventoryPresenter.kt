package io.github.daniele21.localllm.console

import java.util.Locale

class ConsoleInventoryPresenter {
    fun models(snapshot: ConsoleSnapshot): ConsoleScreen {
        val inventory = snapshot.modelInventory
        val cards = mutableListOf<ConsoleCard>()
        inventory.sourceError?.let { error ->
            cards += ConsoleCard(
                title = "Model inventory source",
                lines = listOf(error),
                emphasis = ConsoleEmphasis.NEGATIVE,
            )
        }
        cards += inventorySummary(snapshot)

        when {
            !inventory.available -> cards += emptyCard("Model inventory is not connected")
            inventory.entries.isEmpty() -> cards += emptyCard("No models installed in this source")
            else -> cards += inventory.entries.map { model -> modelCard(model, snapshot.runtime.loadedModel) }
        }

        return ConsoleScreen(
            title = "Installed models",
            subtitle = "Read-only content-addressed model inventory",
            cards = cards,
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
        return ConsoleCard(
            title = "Model inventory",
            lines = listOf(
                "Availability: ${if (inventory.available) "Available" else "Not connected"}",
                "Models: ${inventory.modelCount}",
                "Stored size: ${formatBytes(inventory.totalBytes)}",
                "Active model: ${snapshot.runtime.loadedModel}",
                "Source: ${inventory.source}",
            ),
            emphasis = when {
                inventory.sourceError != null -> ConsoleEmphasis.NEGATIVE
                !inventory.available -> ConsoleEmphasis.WARNING
                else -> ConsoleEmphasis.NEUTRAL
            },
        )
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

    private fun modelCard(model: ConsoleInstalledModel, loadedModel: String): ConsoleCard {
        val active = model.digest.sha256.equals(loadedModel, ignoreCase = true)
        return ConsoleCard(
            title = if (active) {
                "ACTIVE · ${shortDigest(model.digest.sha256)}"
            } else {
                shortDigest(model.digest.sha256)
            },
            lines = listOf(
                "Digest: ${model.digest.sha256}",
                "Size: ${formatBytes(model.sizeBytes)}",
                "Integrity: ${model.integrity.label}",
                "Runtime role: ${if (active) "Loaded" else "Installed"}",
            ),
            emphasis = when {
                active -> ConsoleEmphasis.POSITIVE
                model.integrity == ConsoleModelIntegrity.VERIFIED -> ConsoleEmphasis.POSITIVE
                else -> ConsoleEmphasis.NEUTRAL
            },
        )
    }

    private fun emptyCard(message: String): ConsoleCard = ConsoleCard(
        title = "Installed models",
        lines = listOf(message),
        emphasis = ConsoleEmphasis.WARNING,
    )

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
