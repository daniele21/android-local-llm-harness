package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.CacheHealthProbe
import io.github.daniele21.localllm.observability.CacheHealthSnapshot
import io.github.daniele21.localllm.observability.CacheMaintenanceControl
import io.github.daniele21.localllm.observability.CacheRepairResult

data class ConsoleCacheDescriptor(
    val id: String,
    val snapshot: CacheHealthSnapshot?,
    val repairAvailable: Boolean,
    val sourceError: String? = null,
)

data class ConsoleCacheControlState(
    val available: Boolean,
    val source: String,
    val caches: List<ConsoleCacheDescriptor>,
    val executionInProgress: Boolean = false,
    val lastRepair: ConsoleCacheRepairOutcome? = null,
    val sourceError: String? = null,
)

data class ConsoleCacheRepairOutcome(
    val cacheId: String,
    val result: CacheRepairResult?,
    val sourceError: String? = null,
)

interface ConsoleCacheControl {
    fun snapshot(): ConsoleCacheControlState

    fun repair(cacheId: String): ConsoleCacheRepairOutcome
}

object DisconnectedCacheControl : ConsoleCacheControl {
    override fun snapshot(): ConsoleCacheControlState = ConsoleCacheControlState(
        available = false,
        source = "Not connected",
        caches = emptyList(),
    )

    override fun repair(cacheId: String): ConsoleCacheRepairOutcome = ConsoleCacheRepairOutcome(
        cacheId = cacheId,
        result = null,
        sourceError = CACHE_REPAIR_SOURCE_ERROR,
    )
}

class ContractConsoleCacheControl(
    probes: List<CacheHealthProbe>,
    maintenanceControls: List<CacheMaintenanceControl>,
    private val source: String,
) : ConsoleCacheControl {
    private val probesById = probes.associateBy(CacheHealthProbe::id)
    private val maintenanceById = maintenanceControls.associateBy(CacheMaintenanceControl::id)

    init {
        require(source.isNotBlank()) { "Cache control source must not be blank" }
        require(probesById.size == probes.size) { "Cache health probe IDs must be unique" }
        require(maintenanceById.size == maintenanceControls.size) { "Cache maintenance control IDs must be unique" }
        require(maintenanceById.keys.all(probesById::containsKey)) {
            "Every cache maintenance control must have a matching health probe"
        }
    }

    override fun snapshot(): ConsoleCacheControlState = ConsoleCacheControlState(
        available = true,
        source = source,
        caches = probesById.values
            .map { probe -> descriptor(probe) }
            .sortedBy(ConsoleCacheDescriptor::id),
    )

    override fun repair(cacheId: String): ConsoleCacheRepairOutcome {
        val maintenance = maintenanceById[cacheId]
            ?: return ConsoleCacheRepairOutcome(cacheId, null, CACHE_REPAIR_SOURCE_ERROR)
        return try {
            ConsoleCacheRepairOutcome(
                cacheId = cacheId,
                result = maintenance.repair(),
            )
        } catch (_: RuntimeException) {
            ConsoleCacheRepairOutcome(cacheId, null, CACHE_REPAIR_SOURCE_ERROR)
        }
    }

    private fun descriptor(probe: CacheHealthProbe): ConsoleCacheDescriptor = try {
        ConsoleCacheDescriptor(
            id = probe.id,
            snapshot = probe.snapshot(),
            repairAvailable = maintenanceById.containsKey(probe.id),
        )
    } catch (_: RuntimeException) {
        ConsoleCacheDescriptor(
            id = probe.id,
            snapshot = null,
            repairAvailable = maintenanceById.containsKey(probe.id),
            sourceError = CACHE_HEALTH_SOURCE_ERROR,
        )
    }
}

const val CACHE_HEALTH_SOURCE_ERROR = "Cache health unavailable"
const val CACHE_REPAIR_SOURCE_ERROR = "Cache repair unavailable"
