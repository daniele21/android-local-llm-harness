package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.observability.CacheHealthProbe
import io.github.daniele21.localllm.observability.CacheHealthSnapshot
import io.github.daniele21.localllm.store.ModelStore

class ModelIntegrityCacheHealthProbe(private val cache: ModelIntegrityCache, private val modelStore: ModelStore) : CacheHealthProbe {
    override val id: String = "model-integrity"

    override fun snapshot(): CacheHealthSnapshot = cache.healthSnapshot(modelStore)
}
