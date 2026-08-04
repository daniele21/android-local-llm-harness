package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.observability.CacheMaintenanceControl
import io.github.daniele21.localllm.observability.CacheRepairResult
import io.github.daniele21.localllm.store.ModelStore

class ModelIntegrityCacheMaintenanceControl(private val cache: ModelIntegrityCache, private val modelStore: ModelStore) :
    CacheMaintenanceControl {
    override val id: String = "model-integrity"

    override fun repair(): CacheRepairResult = cache.repair(modelStore)
}
