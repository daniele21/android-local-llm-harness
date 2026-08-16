package io.github.daniele21.localllm.observability.benchmark

import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.ResourceSnapshotProvider
import java.util.ArrayDeque

class MemoryWindowRecorder(private val provider: ResourceSnapshotProvider, private val maximumSamples: Int = DEFAULT_MAXIMUM_SAMPLES) {
    init {
        require(maximumSamples > 0) { "Maximum memory-window sample count must be positive" }
    }

    private val lock = Any()
    private val snapshots = ArrayDeque<ResourceSnapshot>(maximumSamples)

    fun capture(): ResourceSnapshot {
        val snapshot = provider.snapshot()
        add(snapshot)
        return snapshot
    }

    fun add(snapshot: ResourceSnapshot) {
        synchronized(lock) {
            while (snapshots.size >= maximumSamples) {
                snapshots.removeFirst()
            }
            snapshots.addLast(snapshot)
        }
    }

    fun snapshots(): List<ResourceSnapshot> = synchronized(lock) { snapshots.toList() }

    fun summarize(): MemoryWindowSummary = MemoryWindowSummarizer.summarize(snapshots())

    fun clear() {
        synchronized(lock) { snapshots.clear() }
    }

    private companion object {
        const val DEFAULT_MAXIMUM_SAMPLES = 512
    }
}
