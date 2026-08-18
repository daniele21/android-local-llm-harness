package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest
import java.util.concurrent.atomic.AtomicReference

internal enum class ModelResidencyState {
    NOT_RESIDENT,
    LOADING,
    RESIDENT,
    UNLOADING,
}

internal data class ResidentModel(val profileId: String, val handle: BackendModelHandle)

internal data class ModelResidencySnapshot(
    val state: ModelResidencyState,
    val residentModel: ResidentModel?,
    val loadingProfileId: String? = null,
    val loadingDigest: ModelDigest? = null,
)

internal fun interface ModelResidencyProtection {
    fun protects(modelDigest: ModelDigest): Boolean

    companion object {
        val NONE = ModelResidencyProtection { false }
    }
}

/**
 * Owns the in-memory lifecycle and handle identity of the single runtime model.
 *
 * Artifact installation remains owned by ModelStore. Backend initialization/shutdown is a separate
 * runtime concern. Warm-idle and memory-pressure components decide when residency should change but
 * do not own these transitions or the resident handle. Normal unload cannot reserve a resident
 * handle while an activation-residency owner protects its digest.
 */
internal class ModelResidencyLifecycle(
    private val protection: ModelResidencyProtection = ModelResidencyProtection.NONE,
) {
    private val residency = AtomicReference<ModelResidencyRecord>(ModelResidencyRecord.NotResident)

    fun snapshot(): ModelResidencySnapshot = when (val current = residency.get()) {
        ModelResidencyRecord.NotResident -> ModelResidencySnapshot(ModelResidencyState.NOT_RESIDENT, null)

        is ModelResidencyRecord.Loading -> ModelResidencySnapshot(
            state = ModelResidencyState.LOADING,
            residentModel = null,
            loadingProfileId = current.profileId,
            loadingDigest = current.digest,
        )

        is ModelResidencyRecord.Resident -> ModelResidencySnapshot(ModelResidencyState.RESIDENT, current.model)

        is ModelResidencyRecord.Unloading -> ModelResidencySnapshot(ModelResidencyState.UNLOADING, current.model)
    }

    /** Returns a model only when it is fully reusable for new work. */
    fun reusableModelOrNull(): ResidentModel? = (residency.get() as? ModelResidencyRecord.Resident)?.model

    /**
     * Returns the model handle while it remains physically resident. During UNLOADING the handle is
     * still visible until backend release succeeds, matching physical ownership.
     */
    fun residentModelOrNull(): ResidentModel? = when (val current = residency.get()) {
        is ModelResidencyRecord.Resident -> current.model

        is ModelResidencyRecord.Unloading -> current.model

        ModelResidencyRecord.NotResident,
        is ModelResidencyRecord.Loading,
        -> null
    }

    fun hasResidentModel(): Boolean = residentModelOrNull() != null

    fun beginLoad(profileId: String, digest: ModelDigest) {
        check(
            residency.compareAndSet(
                ModelResidencyRecord.NotResident,
                ModelResidencyRecord.Loading(profileId, digest),
            ),
        ) { "Model load requires NOT_RESIDENT state" }
    }

    fun loadSucceeded(model: ResidentModel) {
        while (true) {
            val current = residency.get()
            check(current is ModelResidencyRecord.Loading) { "Model load success requires LOADING state" }
            check(current.profileId == model.profileId) { "Loaded model profile does not match pending load" }
            check(current.digest == model.handle.digest) { "Loaded model digest does not match pending load" }
            if (residency.compareAndSet(current, ModelResidencyRecord.Resident(model))) return
        }
    }

    fun loadFailed() {
        while (true) {
            val current = residency.get()
            check(current is ModelResidencyRecord.Loading) { "Model load failure requires LOADING state" }
            if (residency.compareAndSet(current, ModelResidencyRecord.NotResident)) return
        }
    }

    /** Reserves the resident handle for one physical normal unload when no activation protects it. */
    fun beginUnload(): ResidentModel? {
        while (true) {
            when (val current = residency.get()) {
                ModelResidencyRecord.NotResident,
                is ModelResidencyRecord.Unloading,
                -> return null

                is ModelResidencyRecord.Loading -> error("Cannot unload while model load is in progress")

                is ModelResidencyRecord.Resident -> {
                    if (protection.protects(current.model.handle.digest)) return null
                    if (residency.compareAndSet(current, ModelResidencyRecord.Unloading(current.model))) {
                        return current.model
                    }
                }
            }
        }
    }

    fun unloadSucceeded() {
        while (true) {
            val current = residency.get()
            check(current is ModelResidencyRecord.Unloading) { "Model unload success requires UNLOADING state" }
            if (residency.compareAndSet(current, ModelResidencyRecord.NotResident)) return
        }
    }

    fun unloadFailed() {
        while (true) {
            val current = residency.get()
            check(current is ModelResidencyRecord.Unloading) { "Model unload failure requires UNLOADING state" }
            if (residency.compareAndSet(current, ModelResidencyRecord.Resident(current.model))) return
        }
    }
}

private sealed interface ModelResidencyRecord {
    data object NotResident : ModelResidencyRecord

    data class Loading(val profileId: String, val digest: ModelDigest) : ModelResidencyRecord

    data class Resident(val model: ResidentModel) : ModelResidencyRecord

    data class Unloading(val model: ResidentModel) : ModelResidencyRecord
}
