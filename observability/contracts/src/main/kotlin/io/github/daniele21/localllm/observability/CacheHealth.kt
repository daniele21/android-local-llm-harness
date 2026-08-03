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

interface CacheHealthProbe {
    val id: String

    fun snapshot(): CacheHealthSnapshot
}
