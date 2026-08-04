package io.github.daniele21.localllm.observability

data class CacheHealthSnapshot(val entryCount: Int, val staleEntryCount: Int, val orphanedEntryCount: Int) {
    init {
        require(entryCount >= 0) { "Cache entry count must not be negative" }
        require(staleEntryCount >= 0) { "Stale cache entry count must not be negative" }
        require(orphanedEntryCount >= 0) { "Orphaned cache entry count must not be negative" }
        require(staleEntryCount + orphanedEntryCount <= entryCount) {
            "Cache anomaly counts must not exceed the total entry count"
        }
    }

    val healthyEntryCount: Int = entryCount - staleEntryCount - orphanedEntryCount
    val healthy: Boolean = staleEntryCount == 0 && orphanedEntryCount == 0
}

data class CacheRepairResult(
    val before: CacheHealthSnapshot,
    val after: CacheHealthSnapshot,
    val revalidatedEntryCount: Int,
    val removedEntryCount: Int,
    val failedEntryCount: Int,
) {
    init {
        require(revalidatedEntryCount >= 0) { "Revalidated cache entry count must not be negative" }
        require(removedEntryCount >= 0) { "Removed cache entry count must not be negative" }
        require(failedEntryCount >= 0) { "Failed cache entry count must not be negative" }
        require(revalidatedEntryCount + removedEntryCount + failedEntryCount <= before.staleEntryCount + before.orphanedEntryCount) {
            "Cache repair counts must not exceed the anomaly count before repair"
        }
    }

    val changedEntryCount: Int = revalidatedEntryCount + removedEntryCount
    val successful: Boolean = failedEntryCount == 0 && after.healthy
}

interface CacheHealthProbe {
    val id: String

    fun snapshot(): CacheHealthSnapshot
}

interface CacheMaintenanceControl {
    val id: String

    fun repair(): CacheRepairResult
}
