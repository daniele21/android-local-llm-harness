package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId

@JvmInline
value class UseCaseActivationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Activation ID must not be blank" }
    }
}

@JvmInline
value class ActivationOwnerId(val value: String) {
    init {
        require(value.isNotBlank()) { "Activation owner ID must not be blank" }
    }
}

data class UseCaseActivationLease(
    val activationId: UseCaseActivationId,
    val ownerId: ActivationOwnerId,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val preset: InferencePresetRef,
    val modelDigest: ModelDigest,
    val acquiredAtEpochMs: Long,
    val useCaseRevision: Int,
    val presetRevision: Int,
    val bindingRevision: Int,
) {
    init {
        require(acquiredAtEpochMs >= 0) { "Activation acquisition timestamp must not be negative" }
        require(useCaseRevision > 0) { "Use-case revision must be positive" }
        require(presetRevision > 0) { "Preset revision must be positive" }
        require(bindingRevision > 0) { "Binding revision must be positive" }
        require(preset.version == presetRevision) {
            "Activation preset reference and preset revision must agree"
        }
    }
}

data class UseCaseActivationRequest(
    val ownerId: ActivationOwnerId,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val preset: InferencePresetRef,
    val modelDigest: ModelDigest,
    val acquiredAtEpochMs: Long,
    val useCaseRevision: Int,
    val bindingRevision: Int,
)

enum class ActivationLeaseFailure {
    CAPACITY_REACHED,
    ID_COLLISION,
    NOT_FOUND,
    NOT_OWNED,
}

sealed interface ActivationLeaseResult<out T> {
    data class Success<T>(val value: T) : ActivationLeaseResult<T>

    data class Failure(val reason: ActivationLeaseFailure) : ActivationLeaseResult<Nothing>
}

fun interface ActivationIdFactory {
    fun newId(): UseCaseActivationId
}

class UseCaseActivationLeaseRegistry(
    private val idFactory: ActivationIdFactory,
    private val maxActiveLeases: Int = DEFAULT_MAX_ACTIVE_LEASES,
) {
    private val leases = LinkedHashMap<UseCaseActivationId, UseCaseActivationLease>()

    init {
        require(maxActiveLeases > 0) { "Maximum active activation leases must be positive" }
    }

    val activeCount: Int
        @Synchronized get() = leases.size

    @Synchronized
    fun acquire(request: UseCaseActivationRequest): ActivationLeaseResult<UseCaseActivationLease> {
        if (leases.size >= maxActiveLeases) {
            return ActivationLeaseResult.Failure(ActivationLeaseFailure.CAPACITY_REACHED)
        }
        val activationId = idFactory.newId()
        if (activationId in leases) {
            return ActivationLeaseResult.Failure(ActivationLeaseFailure.ID_COLLISION)
        }
        val lease = UseCaseActivationLease(
            activationId = activationId,
            ownerId = request.ownerId,
            applicationId = request.applicationId,
            useCaseId = request.useCaseId,
            preset = request.preset,
            modelDigest = request.modelDigest,
            acquiredAtEpochMs = request.acquiredAtEpochMs,
            useCaseRevision = request.useCaseRevision,
            presetRevision = request.preset.version,
            bindingRevision = request.bindingRevision,
        )
        leases[activationId] = lease
        return ActivationLeaseResult.Success(lease)
    }

    @Synchronized
    fun find(activationId: UseCaseActivationId): UseCaseActivationLease? = leases[activationId]

    @Synchronized
    fun release(activationId: UseCaseActivationId, ownerId: ActivationOwnerId): ActivationLeaseResult<UseCaseActivationLease> {
        val lease = leases[activationId]
            ?: return ActivationLeaseResult.Failure(ActivationLeaseFailure.NOT_FOUND)
        if (lease.ownerId != ownerId) {
            return ActivationLeaseResult.Failure(ActivationLeaseFailure.NOT_OWNED)
        }
        leases.remove(activationId)
        return ActivationLeaseResult.Success(lease)
    }

    @Synchronized
    fun releaseAll(ownerId: ActivationOwnerId): List<UseCaseActivationLease> {
        val owned = leases.values.filter { it.ownerId == ownerId }
        owned.forEach { leases.remove(it.activationId) }
        return owned
    }

    @Synchronized
    fun activeForOwner(ownerId: ActivationOwnerId): List<UseCaseActivationLease> = leases.values.filter { it.ownerId == ownerId }

    @Synchronized
    fun activeForApplication(applicationId: ApplicationId): List<UseCaseActivationLease> =
        leases.values.filter { it.applicationId == applicationId }

    @Synchronized
    fun activeForModel(modelDigest: ModelDigest): List<UseCaseActivationLease> = leases.values.filter { it.modelDigest == modelDigest }

    companion object {
        const val DEFAULT_MAX_ACTIVE_LEASES = 32
    }
}
