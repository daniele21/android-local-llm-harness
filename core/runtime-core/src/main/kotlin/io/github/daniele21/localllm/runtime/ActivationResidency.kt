package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest

enum class ActivationResidencyFailure {
    MODEL_CONFLICT,
    USE_CASE_ALREADY_ACTIVE,
    LEASE_REJECTED,
}

data class ActivationResidencyConflict(
    val requestedModelDigest: ModelDigest,
    val protectedModelDigest: ModelDigest,
    val activeLeaseCount: Int,
)

data class ActivationResidencyRelease(
    val releasedLeases: List<UseCaseActivationLease>,
    val remainingLeaseCount: Int,
    val warmRetentionByModelMs: Map<ModelDigest, Long>,
)

sealed interface ActivationResidencyResult<out T> {
    data class Success<T>(val value: T) : ActivationResidencyResult<T>

    data class Failure(
        val reason: ActivationResidencyFailure,
        val leaseFailure: ActivationLeaseFailure? = null,
        val conflict: ActivationResidencyConflict? = null,
    ) : ActivationResidencyResult<Nothing>
}

/**
 * Connects product-level activation ownership to the one-resident-model runtime policy.
 *
 * The lease registry remains authoritative for owner/application identity. This coordinator adds
 * residency semantics: compatible activations may share one model, a different-model activation is
 * rejected while another digest is protected, normal unload can query protection explicitly, and
 * the final release returns the resolved warm-retention duration that must start after demand ends.
 */
class ActivationResidencyCoordinator(private val leases: UseCaseActivationLeaseRegistry) {
    private val warmRetentionByActivation = LinkedHashMap<UseCaseActivationId, Long>()

    @Synchronized
    fun acquire(request: UseCaseActivationRequest, retainModelWarmMs: Long): ActivationResidencyResult<UseCaseActivationLease> =
        acquireLocked(request, retainModelWarmMs)

    /**
     * Consumer API v1.2 does not carry an activation identity into open-session calls. Keep routing
     * deterministic by allowing at most one activation for the same owner/application/use-case.
     * Other owners may still share the same resolved model concurrently.
     */
    @Synchronized
    fun acquireExclusiveUseCase(
        request: UseCaseActivationRequest,
        retainModelWarmMs: Long,
    ): ActivationResidencyResult<UseCaseActivationLease> {
        val duplicate = leases.activeForOwner(request.ownerId).any { lease ->
            lease.applicationId == request.applicationId && lease.useCaseId == request.useCaseId
        }
        if (duplicate) {
            return ActivationResidencyResult.Failure(ActivationResidencyFailure.USE_CASE_ALREADY_ACTIVE)
        }
        return acquireLocked(request, retainModelWarmMs)
    }

    @Synchronized
    fun release(activationId: UseCaseActivationId, ownerId: ActivationOwnerId): ActivationResidencyResult<ActivationResidencyRelease> =
        when (val released = leases.release(activationId, ownerId)) {
            is ActivationLeaseResult.Failure -> ActivationResidencyResult.Failure(
                reason = ActivationResidencyFailure.LEASE_REJECTED,
                leaseFailure = released.reason,
            )

            is ActivationLeaseResult.Success -> {
                val lease = released.value
                val retention = warmRetentionByActivation.remove(lease.activationId) ?: 0L
                val warmRetention = if (leases.activeForModel(lease.modelDigest).isEmpty()) {
                    mapOf(lease.modelDigest to retention)
                } else {
                    emptyMap()
                }
                ActivationResidencyResult.Success(
                    ActivationResidencyRelease(
                        releasedLeases = listOf(lease),
                        remainingLeaseCount = leases.activeCount,
                        warmRetentionByModelMs = warmRetention,
                    ),
                )
            }
        }

    @Synchronized
    fun releaseAll(ownerId: ActivationOwnerId): ActivationResidencyRelease {
        val released = leases.releaseAll(ownerId)
        val releasedPolicies = released.associateWith { lease ->
            warmRetentionByActivation.remove(lease.activationId) ?: 0L
        }
        val warmRetention =
            released
                .groupBy(UseCaseActivationLease::modelDigest)
                .mapNotNull { (digest, modelLeases) ->
                    if (leases.activeForModel(digest).isNotEmpty()) {
                        null
                    } else {
                        digest to modelLeases.maxOf { releasedPolicies.getValue(it) }
                    }
                }.toMap()
        return ActivationResidencyRelease(
            releasedLeases = released,
            remainingLeaseCount = leases.activeCount,
            warmRetentionByModelMs = warmRetention,
        )
    }

    fun protects(modelDigest: ModelDigest): Boolean = leases.activeForModel(modelDigest).isNotEmpty()

    fun canActivate(modelDigest: ModelDigest): Boolean = leases.activeLeases().all { it.modelDigest == modelDigest }

    fun activeLeaseCount(modelDigest: ModelDigest): Int = leases.activeForModel(modelDigest).size

    private fun acquireLocked(
        request: UseCaseActivationRequest,
        retainModelWarmMs: Long,
    ): ActivationResidencyResult<UseCaseActivationLease> {
        require(retainModelWarmMs >= 0) { "Model warm-retention duration must not be negative" }
        val active = leases.activeLeases()
        val protectedDigest = active.firstOrNull()?.modelDigest
        if (protectedDigest != null && protectedDigest != request.modelDigest) {
            return ActivationResidencyResult.Failure(
                reason = ActivationResidencyFailure.MODEL_CONFLICT,
                conflict = ActivationResidencyConflict(
                    requestedModelDigest = request.modelDigest,
                    protectedModelDigest = protectedDigest,
                    activeLeaseCount = active.size,
                ),
            )
        }

        return when (val acquired = leases.acquire(request)) {
            is ActivationLeaseResult.Success -> {
                warmRetentionByActivation[acquired.value.activationId] = retainModelWarmMs
                ActivationResidencyResult.Success(acquired.value)
            }

            is ActivationLeaseResult.Failure -> ActivationResidencyResult.Failure(
                reason = ActivationResidencyFailure.LEASE_REJECTED,
                leaseFailure = acquired.reason,
            )
        }
    }
}
