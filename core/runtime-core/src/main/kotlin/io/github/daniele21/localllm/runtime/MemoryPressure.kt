package io.github.daniele21.localllm.runtime

enum class RuntimeMemoryPressure {
    UI_HIDDEN,
    BACKGROUND,
    LOW_MEMORY,
}

enum class RuntimeMemoryAction {
    NONE,
    UNLOAD_IDLE_MODEL,
    CANCEL_AND_RELEASE_ALL,
}

data class RuntimeMemoryResourceSnapshot(
    val modelLoaded: Boolean,
    val activeSessions: Int,
    val activeGeneration: Boolean,
    val queuedGenerations: Int,
)

data class RuntimeMemoryResult(
    val action: RuntimeMemoryAction,
    val cancelledRequests: Int,
    val modelUnloaded: Boolean,
    val deferred: Boolean,
)

class RuntimeMemoryPolicy {
    fun decide(
        pressure: RuntimeMemoryPressure,
        resources: RuntimeMemoryResourceSnapshot,
    ): RuntimeMemoryAction {
        if (!resources.modelLoaded && resources.activeSessions == 0 &&
            !resources.activeGeneration && resources.queuedGenerations == 0
        ) {
            return RuntimeMemoryAction.NONE
        }

        val idle = resources.activeSessions == 0 &&
            !resources.activeGeneration && resources.queuedGenerations == 0
        return when (pressure) {
            RuntimeMemoryPressure.UI_HIDDEN,
            RuntimeMemoryPressure.BACKGROUND,
            -> if (resources.modelLoaded && idle) {
                RuntimeMemoryAction.UNLOAD_IDLE_MODEL
            } else {
                RuntimeMemoryAction.NONE
            }

            RuntimeMemoryPressure.LOW_MEMORY -> if (idle) {
                RuntimeMemoryAction.UNLOAD_IDLE_MODEL
            } else {
                RuntimeMemoryAction.CANCEL_AND_RELEASE_ALL
            }
        }
    }
}
