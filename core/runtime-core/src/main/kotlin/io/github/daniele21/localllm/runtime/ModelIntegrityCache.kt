package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.observability.CacheHealthSnapshot
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import java.util.concurrent.ConcurrentHashMap

class ModelIntegrityCache {
    private val verified = ConcurrentHashMap<ModelDigest, VerificationStamp>()

    fun verify(modelStore: ModelStore, storedModel: StoredModel): VerificationResult {
        val current = VerificationStamp.from(storedModel)
        val cached = verified[storedModel.digest]
        if (cached == current || (cached == null && storedModel.verified)) {
            verified[storedModel.digest] = current
            return VerificationResult(
                valid = true,
                actualDigest = storedModel.digest,
                detail = "Stored model integrity reused from a matching file stamp",
            )
        }

        val result = modelStore.verify(storedModel.digest)
        if (result.valid) {
            verified[storedModel.digest] = VerificationStamp.from(storedModel)
        } else {
            verified.remove(storedModel.digest)
        }
        return result
    }

    fun healthSnapshot(modelStore: ModelStore): CacheHealthSnapshot {
        val installedByDigest = modelStore.snapshot().entries.associateBy(StoredModel::digest)
        val cachedEntries = verified.entries.map { it.key to it.value }
        var staleEntryCount = 0
        var orphanedEntryCount = 0
        cachedEntries.forEach { (digest, stamp) ->
            val installed = installedByDigest[digest]
            if (installed == null) {
                orphanedEntryCount += 1
            } else if (stamp != VerificationStamp.from(installed)) {
                staleEntryCount += 1
            }
        }
        return CacheHealthSnapshot(
            entryCount = cachedEntries.size,
            staleEntryCount = staleEntryCount,
            orphanedEntryCount = orphanedEntryCount,
        )
    }

    fun invalidate(digest: ModelDigest) {
        verified.remove(digest)
    }

    fun clear() {
        verified.clear()
    }

    fun size(): Int = verified.size

    private data class VerificationStamp(val absolutePath: String, val sizeBytes: Long, val lastModifiedMs: Long) {
        companion object {
            fun from(storedModel: StoredModel): VerificationStamp = VerificationStamp(
                absolutePath = storedModel.file.absolutePath,
                sizeBytes = storedModel.file.length(),
                lastModifiedMs = storedModel.file.lastModified(),
            )
        }
    }
}
