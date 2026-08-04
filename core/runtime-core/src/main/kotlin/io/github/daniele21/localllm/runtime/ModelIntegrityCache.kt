package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.observability.CacheHealthSnapshot
import io.github.daniele21.localllm.observability.CacheRepairResult
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
        return buildHealthSnapshot(installedByDigest, cachedEntries)
    }

    fun repair(modelStore: ModelStore): CacheRepairResult {
        val installedByDigest = modelStore.snapshot().entries.associateBy(StoredModel::digest)
        val cachedEntries = verified.entries.map { it.key to it.value }
        val before = buildHealthSnapshot(installedByDigest, cachedEntries)
        val outcomes = cachedEntries.map { (digest, stamp) ->
            repairEntry(
                digest = digest,
                stamp = stamp,
                installed = installedByDigest[digest],
                modelStore = modelStore,
            )
        }

        return CacheRepairResult(
            before = before,
            after = healthSnapshot(modelStore),
            revalidatedEntryCount = outcomes.count { it == RepairEntryOutcome.REVALIDATED },
            removedEntryCount = outcomes.count { it == RepairEntryOutcome.REMOVED },
            failedEntryCount = outcomes.count { it == RepairEntryOutcome.FAILED },
        )
    }

    fun invalidate(digest: ModelDigest) {
        verified.remove(digest)
    }

    fun clear() {
        verified.clear()
    }

    fun size(): Int = verified.size

    private fun repairEntry(
        digest: ModelDigest,
        stamp: VerificationStamp,
        installed: StoredModel?,
        modelStore: ModelStore,
    ): RepairEntryOutcome = when {
        installed == null -> removeEntry(digest, stamp)
        stamp == VerificationStamp.from(installed) -> RepairEntryOutcome.UNCHANGED
        else -> revalidateEntry(digest, stamp, installed, modelStore)
    }

    private fun removeEntry(digest: ModelDigest, stamp: VerificationStamp): RepairEntryOutcome =
        if (verified.remove(digest, stamp)) RepairEntryOutcome.REMOVED else RepairEntryOutcome.UNCHANGED

    @Suppress("TooGenericExceptionCaught")
    private fun revalidateEntry(
        digest: ModelDigest,
        stamp: VerificationStamp,
        installed: StoredModel,
        modelStore: ModelStore,
    ): RepairEntryOutcome = try {
        val result = modelStore.verify(digest)
        if (result.valid) {
            val current = VerificationStamp.from(installed)
            if (verified.replace(digest, stamp, current)) {
                RepairEntryOutcome.REVALIDATED
            } else {
                RepairEntryOutcome.UNCHANGED
            }
        } else {
            removeEntry(digest, stamp)
        }
    } catch (_: RuntimeException) {
        RepairEntryOutcome.FAILED
    }

    private fun buildHealthSnapshot(
        installedByDigest: Map<ModelDigest, StoredModel>,
        cachedEntries: List<Pair<ModelDigest, VerificationStamp>>,
    ): CacheHealthSnapshot {
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

    private enum class RepairEntryOutcome {
        REVALIDATED,
        REMOVED,
        FAILED,
        UNCHANGED,
    }

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
