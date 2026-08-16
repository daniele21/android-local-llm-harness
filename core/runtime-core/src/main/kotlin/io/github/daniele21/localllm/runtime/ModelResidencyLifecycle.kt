package io.github.daniele21.localllm.runtime

import java.util.concurrent.atomic.AtomicReference

internal enum class ModelResidencyState {
    EMPTY,
    LOADING,
    RESIDENT,
    UNLOADING,
}

/**
 * Owns only the physical model-residency transition contract.
 *
 * Artifact installation remains owned by ModelStore, warm-idle timing by
 * WarmIdleResidencyController, memory-pressure decisions by RuntimeMemoryPolicy,
 * and the backend model handle by RuntimeOrchestrator.
 */
internal class ModelResidencyLifecycle {
    private val state = AtomicReference(ModelResidencyState.EMPTY)

    fun state(): ModelResidencyState = state.get()

    fun beginLoad() {
        check(state.compareAndSet(ModelResidencyState.EMPTY, ModelResidencyState.LOADING)) {
            "Model load can start only from EMPTY"
        }
    }

    fun loadSucceeded() {
        check(state.compareAndSet(ModelResidencyState.LOADING, ModelResidencyState.RESIDENT)) {
            "Model load can complete only from LOADING"
        }
    }

    fun loadFailed() {
        check(state.compareAndSet(ModelResidencyState.LOADING, ModelResidencyState.EMPTY)) {
            "Model load can fail only from LOADING"
        }
    }

    fun tryBeginUnload(): Boolean {
        while (true) {
            when (state.get()) {
                ModelResidencyState.EMPTY -> return false
                ModelResidencyState.LOADING -> error("Model unload cannot start while loading")
                ModelResidencyState.UNLOADING -> return false
                ModelResidencyState.RESIDENT -> {
                    if (state.compareAndSet(ModelResidencyState.RESIDENT, ModelResidencyState.UNLOADING)) {
                        return true
                    }
                }
            }
        }
    }

    fun unloadSucceeded() {
        check(state.compareAndSet(ModelResidencyState.UNLOADING, ModelResidencyState.EMPTY)) {
            "Model unload can complete only from UNLOADING"
        }
    }

    fun unloadFailed() {
        check(state.compareAndSet(ModelResidencyState.UNLOADING, ModelResidencyState.RESIDENT)) {
            "Model unload can fail only from UNLOADING"
        }
    }
}
