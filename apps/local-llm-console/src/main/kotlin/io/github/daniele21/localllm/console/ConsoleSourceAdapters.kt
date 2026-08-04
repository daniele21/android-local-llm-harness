package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.store.ModelStore

class ModelStoreInventoryProvider(private val modelStore: ModelStore, private val source: String) : ConsoleModelInventoryProvider {
    override fun snapshot(): ConsoleModelInventory {
        val snapshot = modelStore.snapshot()
        return ConsoleModelInventory(
            available = true,
            modelCount = snapshot.modelCount,
            totalBytes = snapshot.totalBytes,
            entries = snapshot.entries
                .map { stored ->
                    ConsoleInstalledModel(
                        digest = stored.digest,
                        sizeBytes = stored.sizeBytes,
                        integrity = if (stored.verified) {
                            ConsoleModelIntegrity.VERIFIED
                        } else {
                            ConsoleModelIntegrity.NOT_CHECKED
                        },
                    )
                }
                .sortedBy { it.digest.sha256 },
            source = source,
        )
    }
}

class LocalLlmRuntimeStateProvider(
    private val client: LocalLlmClient,
    private val backend: String,
    private val source: String = "In process",
) : ConsoleRuntimeStateProvider {
    override fun snapshot(): ConsoleRuntimeState {
        val snapshot = client.runtimeSnapshot()
        return ConsoleRuntimeState(
            status = snapshot.state.name,
            backend = backend,
            loadedModel = snapshot.loadedModel?.sha256 ?: "None",
            activeSessions = snapshot.activeSessions,
            queueDepth = snapshot.queuedRequests,
            source = source,
            connected = true,
        )
    }
}
