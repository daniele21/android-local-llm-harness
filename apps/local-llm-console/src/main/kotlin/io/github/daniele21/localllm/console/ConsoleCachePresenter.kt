package io.github.daniele21.localllm.console

class ConsoleCachePresenter {
    fun present(snapshot: ConsoleSnapshot): ConsoleScreen {
        val state = snapshot.cacheControl
        val cards = mutableListOf<ConsoleCard>()
        state.sourceError?.let { error ->
            cards += ConsoleCard(
                title = "Cache source",
                lines = listOf(error),
                emphasis = ConsoleEmphasis.NEGATIVE,
            )
        }
        cards += sourceCard(state)
        state.lastRepair?.let { outcome -> cards += repairOutcomeCard(outcome) }
        cards += state.caches.map(::cacheCard).ifEmpty {
            listOf(
                ConsoleCard(
                    title = "Cache inventory",
                    lines = listOf(
                        if (state.available) {
                            "No cache health probes registered"
                        } else {
                            "Cache diagnostics source not connected"
                        },
                    ),
                    emphasis = ConsoleEmphasis.WARNING,
                ),
            )
        }

        val actions = state.caches.mapNotNull { descriptor ->
            val cacheSnapshot = descriptor.snapshot
            if (!descriptor.repairAvailable || cacheSnapshot == null || cacheSnapshot.healthy) return@mapNotNull null
            ConsoleAction(
                type = ConsoleActionType.REPAIR_CACHE,
                label = "Repair ${descriptor.id}",
                cacheId = descriptor.id,
                enabled = state.available && !state.executionInProgress,
            )
        }

        return ConsoleScreen(
            title = "Cache health",
            subtitle = "Runtime-owned cache inspection and explicit anomaly repair",
            cards = cards,
            actions = actions,
        )
    }

    fun summary(snapshot: ConsoleSnapshot): ConsoleCard {
        val state = snapshot.cacheControl
        val availableSnapshots = state.caches.mapNotNull(ConsoleCacheDescriptor::snapshot)
        val unhealthyCount = availableSnapshots.count { !it.healthy }
        val unavailableCount = state.caches.count { it.snapshot == null }
        return ConsoleCard(
            title = "Cache health",
            lines = listOf(
                "Connected: ${state.available}",
                "Registered caches: ${state.caches.size}",
                "Healthy caches: ${availableSnapshots.count { it.healthy }}",
                "Unhealthy caches: $unhealthyCount",
                "Unavailable probes: $unavailableCount",
                "Source: ${state.source}",
            ),
            emphasis = when {
                state.sourceError != null || unavailableCount > 0 -> ConsoleEmphasis.NEGATIVE
                !state.available || unhealthyCount > 0 -> ConsoleEmphasis.WARNING
                else -> ConsoleEmphasis.POSITIVE
            },
        )
    }

    private fun sourceCard(state: ConsoleCacheControlState): ConsoleCard = ConsoleCard(
        title = "Cache diagnostics control",
        lines = listOf(
            "Connected: ${state.available}",
            "Source: ${state.source}",
            "Registered caches: ${state.caches.size}",
            "Execution: ${if (state.executionInProgress) "Running" else "Idle"}",
            "Repair policy: explicit actions only",
        ),
        emphasis = when {
            state.sourceError != null -> ConsoleEmphasis.NEGATIVE
            !state.available -> ConsoleEmphasis.WARNING
            else -> ConsoleEmphasis.NEUTRAL
        },
    )

    private fun cacheCard(descriptor: ConsoleCacheDescriptor): ConsoleCard {
        descriptor.sourceError?.let { error ->
            return ConsoleCard(
                title = descriptor.id,
                lines = listOf(
                    "Status: Unavailable",
                    "Repair available: ${descriptor.repairAvailable}",
                    "Detail: $error",
                ),
                emphasis = ConsoleEmphasis.NEGATIVE,
            )
        }
        val snapshot = requireNotNull(descriptor.snapshot)
        return ConsoleCard(
            title = descriptor.id,
            lines = listOf(
                "Status: ${if (snapshot.healthy) "Healthy" else "Anomalies detected"}",
                "Entries: ${snapshot.entryCount}",
                "Healthy entries: ${snapshot.healthyEntryCount}",
                "Stale entries: ${snapshot.staleEntryCount}",
                "Orphaned entries: ${snapshot.orphanedEntryCount}",
                "Repair available: ${descriptor.repairAvailable}",
            ),
            emphasis = if (snapshot.healthy) ConsoleEmphasis.POSITIVE else ConsoleEmphasis.WARNING,
        )
    }

    private fun repairOutcomeCard(outcome: ConsoleCacheRepairOutcome): ConsoleCard {
        outcome.sourceError?.let { error ->
            return ConsoleCard(
                title = "Repair ${outcome.cacheId}",
                lines = listOf(error),
                emphasis = ConsoleEmphasis.NEGATIVE,
            )
        }
        val result = requireNotNull(outcome.result)
        return ConsoleCard(
            title = "Repair ${outcome.cacheId}",
            lines = listOf(
                "Before: ${result.before.staleEntryCount} stale · ${result.before.orphanedEntryCount} orphaned",
                "Revalidated: ${result.revalidatedEntryCount}",
                "Removed: ${result.removedEntryCount}",
                "Failed: ${result.failedEntryCount}",
                "After: ${result.after.staleEntryCount} stale · ${result.after.orphanedEntryCount} orphaned",
                "Successful: ${result.successful}",
            ),
            emphasis = when {
                result.successful -> ConsoleEmphasis.POSITIVE
                result.failedEntryCount > 0 -> ConsoleEmphasis.NEGATIVE
                else -> ConsoleEmphasis.WARNING
            },
        )
    }
}
