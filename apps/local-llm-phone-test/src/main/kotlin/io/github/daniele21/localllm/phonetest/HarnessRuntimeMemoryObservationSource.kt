package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.ResourceSnapshotProvider
import io.github.daniele21.localllm.runtime.RuntimeMemoryObservation

internal class HarnessRuntimeMemoryObservationSource(private val provider: ResourceSnapshotProvider) {
    fun observe(): RuntimeMemoryObservation = provider.snapshot().toRuntimeMemoryObservation()
}

internal fun ResourceSnapshot.toRuntimeMemoryObservation(): RuntimeMemoryObservation = RuntimeMemoryObservation(
    processPssBytes = processPssBytes,
    nativeHeapBytes = nativeHeapBytes,
    javaHeapUsedBytes = javaHeapUsedBytes,
    availableMemoryBytes = availableMemoryBytes,
    lowMemory = lowMemory,
)
