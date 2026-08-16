package io.github.daniele21.localllm.runtime

data class RuntimeMemoryObservation(
    val processPssBytes: Long? = null,
    val nativeHeapBytes: Long? = null,
    val javaHeapUsedBytes: Long? = null,
    val availableMemoryBytes: Long? = null,
    val lowMemory: Boolean? = null,
) {
    init {
        listOf(processPssBytes, nativeHeapBytes, javaHeapUsedBytes, availableMemoryBytes).forEach { value ->
            require(value == null || value >= 0L) { "Memory observation byte values must not be negative" }
        }
    }
}

data class RuntimeMemoryBudget(
    val minimumAvailableBytes: Long,
    val safetyReserveBytes: Long,
    val maxProcessPssBytes: Long? = null,
    val maxResidentContexts: Int,
    val requireHeadroomObservation: Boolean = true,
) {
    init {
        require(minimumAvailableBytes >= 0L) { "Minimum available memory must not be negative" }
        require(safetyReserveBytes >= 0L) { "Memory safety reserve must not be negative" }
        require(maxProcessPssBytes == null || maxProcessPssBytes > 0L) { "Maximum process PSS must be positive" }
        require(maxResidentContexts > 0) { "Maximum resident contexts must be positive" }
    }
}

enum class MemoryCostSource {
    THEORETICAL,
    CANDIDATE,
    MEASURED,
}

data class MemoryCostEstimate(
    val residentBytes: Long,
    val peakIncrementalBytes: Long,
    val source: MemoryCostSource,
    val profileId: String,
) {
    init {
        require(residentBytes >= 0L) { "Resident memory estimate must not be negative" }
        require(peakIncrementalBytes >= residentBytes) {
            "Peak incremental memory must be at least the resident estimate"
        }
        require(profileId.isNotBlank()) { "Memory cost profile ID must not be blank" }
    }
}

data class RuntimeResidencySnapshot(
    val modelLoaded: Boolean,
    val residentContexts: Int,
    val activeGeneration: Boolean,
    val queuedGenerations: Int,
) {
    init {
        require(residentContexts >= 0) { "Resident context count must not be negative" }
        require(queuedGenerations >= 0) { "Queued generation count must not be negative" }
    }
}

enum class MemoryAdmissionResource {
    MODEL,
    CONTEXT,
}

data class MemoryAdmissionRequest(
    val resource: MemoryAdmissionResource,
    val estimate: MemoryCostEstimate,
    val residency: RuntimeResidencySnapshot,
)

enum class MemoryAdmissionRejectReason {
    PLATFORM_LOW_MEMORY,
    HEADROOM_OBSERVATION_REQUIRED,
    RESIDENT_CONTEXT_LIMIT,
    AVAILABLE_MEMORY_FLOOR,
    PROCESS_PSS_LIMIT,
    BYTE_ARITHMETIC_OVERFLOW,
}

sealed interface MemoryAdmissionDecision {
    data object Allow : MemoryAdmissionDecision

    data class Reject(val reason: MemoryAdmissionRejectReason) : MemoryAdmissionDecision
}

class MemoryAdmissionController(private val budget: RuntimeMemoryBudget) {
    fun decide(observation: RuntimeMemoryObservation, request: MemoryAdmissionRequest): MemoryAdmissionDecision = when {
        observation.lowMemory == true -> MemoryAdmissionDecision.Reject(MemoryAdmissionRejectReason.PLATFORM_LOW_MEMORY)

        contextLimitReached(request) -> MemoryAdmissionDecision.Reject(MemoryAdmissionRejectReason.RESIDENT_CONTEXT_LIMIT)

        headroomObservationMissing(observation) -> {
            MemoryAdmissionDecision.Reject(MemoryAdmissionRejectReason.HEADROOM_OBSERVATION_REQUIRED)
        }

        else -> availableMemoryDecision(observation, request)
            ?: processPssDecision(observation, request)
            ?: MemoryAdmissionDecision.Allow
    }

    private fun contextLimitReached(request: MemoryAdmissionRequest): Boolean = request.resource == MemoryAdmissionResource.CONTEXT &&
        request.residency.residentContexts >= budget.maxResidentContexts

    private fun headroomObservationMissing(observation: RuntimeMemoryObservation): Boolean {
        val hasAvailableSignal = observation.availableMemoryBytes != null
        val hasProcessSignal = budget.maxProcessPssBytes != null && observation.processPssBytes != null
        return budget.requireHeadroomObservation && !hasAvailableSignal && !hasProcessSignal
    }

    private fun availableMemoryDecision(
        observation: RuntimeMemoryObservation,
        request: MemoryAdmissionRequest,
    ): MemoryAdmissionDecision.Reject? {
        val available = observation.availableMemoryBytes ?: return null
        val required = safeAdd(
            request.estimate.peakIncrementalBytes,
            budget.safetyReserveBytes,
            budget.minimumAvailableBytes,
        ) ?: return MemoryAdmissionDecision.Reject(MemoryAdmissionRejectReason.BYTE_ARITHMETIC_OVERFLOW)
        return if (available < required) {
            MemoryAdmissionDecision.Reject(MemoryAdmissionRejectReason.AVAILABLE_MEMORY_FLOOR)
        } else {
            null
        }
    }

    private fun processPssDecision(
        observation: RuntimeMemoryObservation,
        request: MemoryAdmissionRequest,
    ): MemoryAdmissionDecision.Reject? {
        val maximum = budget.maxProcessPssBytes ?: return null
        val current = observation.processPssBytes ?: return null
        val projectedPeak = safeAdd(current, request.estimate.peakIncrementalBytes)
            ?: return MemoryAdmissionDecision.Reject(MemoryAdmissionRejectReason.BYTE_ARITHMETIC_OVERFLOW)
        return if (projectedPeak > maximum) {
            MemoryAdmissionDecision.Reject(MemoryAdmissionRejectReason.PROCESS_PSS_LIMIT)
        } else {
            null
        }
    }

    private fun safeAdd(vararg values: Long): Long? = try {
        values.fold(0L, Math::addExact)
    } catch (_: ArithmeticException) {
        null
    }
}
