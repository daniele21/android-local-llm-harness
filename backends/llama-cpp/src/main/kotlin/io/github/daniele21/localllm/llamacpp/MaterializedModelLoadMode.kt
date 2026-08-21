package io.github.daniele21.localllm.llamacpp

/**
 * Materialized load-mode identity for the legacy mmap/mlock product policy.
 *
 * The candidate llama.cpp API represents the same four combinations through
 * `llama_load_mode`; keeping this explicit in execution evidence prevents two
 * materially different load policies from sharing a fingerprint while the
 * production pin still exposes the legacy booleans.
 */
internal enum class MaterializedModelLoadMode {
    NONE,
    MMAP,
    MLOCK,
    MMAP_MLOCK,
}

internal val NativeModelExecutionRequest.materializedLoadMode: MaterializedModelLoadMode
    get() = when {
        useMmap && useMlock -> MaterializedModelLoadMode.MMAP_MLOCK
        useMmap -> MaterializedModelLoadMode.MMAP
        useMlock -> MaterializedModelLoadMode.MLOCK
        else -> MaterializedModelLoadMode.NONE
    }
