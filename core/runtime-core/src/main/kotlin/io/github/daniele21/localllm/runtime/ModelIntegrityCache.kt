package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import java.util.concurrent.ConcurrentHashMap

class ModelIntegrityCache {
    private val verified = ConcurrentHashMap<ModelDigest, VerificationStamp>()

    fun verify(modelStore: ModelStore, storedModel: StoredModel): VerificationResult {
        val current = VerificationStamp.from(storedModel)
        if (storedModel.verified || verified[storedModel.digest] == current) {
            verified[storedModel.digest] = current
            return VerificationResult(
                valid = true,
                actualDigest = storedModel.digest,
                detail = "Stored model integrity reused from a matching file stamp",
            )
        }

        val result = modelStore.verify(storedModel.digest)
        if (result.valid) {
            verified[storedModel.digest] = VerificationStamp.from(storedModel.file.length(), storedModel)
        } else {
            verified.remove(storedModel.digest)
        }
        return result
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
            fun from(storedModel: StoredModel): VerificationStamp = from(storedModel.sizeBytes, storedModel)

            fun from(sizeBytes: Long, storedModel: StoredModel): VerificationStamp = VerificationStamp(
                absolutePath = storedModel.file.absolutePath,
                sizeBytes = sizeBytes,
                lastModifiedMs = storedModel.file.lastModified(),
            )
        }
    }
}
