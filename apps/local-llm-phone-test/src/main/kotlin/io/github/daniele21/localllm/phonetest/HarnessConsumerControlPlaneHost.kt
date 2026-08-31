package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivation
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCase
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCasesResult
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneErrorCode
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneFailure
import io.github.daniele21.localllm.contracts.ConsumerDeactivationResult
import io.github.daniele21.localllm.contracts.ConsumerPublishedPreset
import io.github.daniele21.localllm.contracts.ConsumerPublishedPresetsResult
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.integration.servicehost.ConsumerControlPlaneHost
import io.github.daniele21.localllm.models.AssignedUseCaseDiscovery
import io.github.daniele21.localllm.models.AssignedUseCaseDiscoveryFailure
import io.github.daniele21.localllm.models.AssignedUseCaseDiscoveryResult
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.HostExecutionEnvironment
import io.github.daniele21.localllm.models.HostExecutionFailureCode
import io.github.daniele21.localllm.models.HostExecutionRequest
import io.github.daniele21.localllm.models.HostExecutionResolution
import io.github.daniele21.localllm.models.HostExecutionResolver
import io.github.daniele21.localllm.models.PublishedPresetDiscovery
import io.github.daniele21.localllm.models.PublishedPresetDiscoveryFailure
import io.github.daniele21.localllm.models.PublishedPresetDiscoveryResult
import io.github.daniele21.localllm.models.Qwen35RuntimeTuningProfiles
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.runtime.ActivationLeaseFailure
import io.github.daniele21.localllm.runtime.ActivationOwnerId
import io.github.daniele21.localllm.runtime.ActivationResidencyFailure
import io.github.daniele21.localllm.runtime.ActivationResidencyResult
import io.github.daniele21.localllm.runtime.UseCaseActivationId
import io.github.daniele21.localllm.runtime.UseCaseActivationLease
import io.github.daniele21.localllm.runtime.UseCaseActivationRequest
import io.github.daniele21.localllm.store.ModelStore

private const val LLAMA_CPP_BACKEND_ID = "llama.cpp"

internal class HarnessConsumerControlPlaneHost(
    private val store: HostControlPlaneStore,
    private val modelStore: ModelStore,
    private val runtimeControl: HarnessConsumerRuntimeControl,
    private val epochClock: () -> Long = System::currentTimeMillis,
    private val onWarmRetention: (ModelDigest, Long) -> Unit = { _, _ -> },
) : ConsumerControlPlaneHost {
    constructor(
        store: HostControlPlaneStore,
        modelStore: ModelStore,
        runtimeGraph: HarnessRuntimeGraph,
        epochClock: () -> Long = System::currentTimeMillis,
        onWarmRetention: (ModelDigest, Long) -> Unit = { _, _ -> },
    ) : this(
        store = store,
        modelStore = modelStore,
        runtimeControl = HarnessRuntimeGraphConsumerControl(runtimeGraph),
        epochClock = epochClock,
        onWarmRetention = onWarmRetention,
    )

    private val resolver = HostExecutionResolver(store)
    private val useCaseDiscovery = AssignedUseCaseDiscovery(store)
    private val presetDiscovery = PublishedPresetDiscovery(store)

    override fun assignedUseCases(applicationId: ApplicationId): ConsumerAssignedUseCasesResult =
        when (val result = useCaseDiscovery.discover(applicationId)) {
            is AssignedUseCaseDiscoveryResult.Success -> ConsumerAssignedUseCasesResult.Available(
                result.assignments.map { assignment ->
                    ConsumerAssignedUseCase(
                        useCaseId = assignment.useCaseId,
                        useCaseRevision = assignment.useCaseRevision,
                        bindingRevision = assignment.bindingRevision,
                        displayName = assignment.displayName,
                        description = assignment.description,
                        isDefault = assignment.isDefault,
                    )
                },
            )

            is AssignedUseCaseDiscoveryResult.Failure -> ConsumerAssignedUseCasesResult.Rejected(
                result.reason.toConsumerFailure(),
            )
        }

    override fun publishedPresets(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerPublishedPresetsResult =
        when (val result = presetDiscovery.discover(applicationId, useCaseId)) {
            is PublishedPresetDiscoveryResult.Success -> ConsumerPublishedPresetsResult.Available(
                useCaseId = useCaseId,
                bindingRevision = result.bindingRevision,
                presets = result.presets.map { preset ->
                    ConsumerPublishedPreset(
                        preset = InferencePresetRef(InferencePresetId(preset.presetId), preset.revision),
                        displayName = preset.displayName,
                        description = preset.description,
                        isDefault = preset.isDefault,
                    )
                },
            )

            is PublishedPresetDiscoveryResult.Failure -> ConsumerPublishedPresetsResult.Rejected(
                result.reason.toConsumerFailure(),
            )
        }

    override fun activate(ownerId: String, applicationId: ApplicationId, request: ConsumerActivationRequest): ConsumerActivationResult {
        val resolution = resolver.resolve(
            HostExecutionRequest(
                applicationId = applicationId,
                useCaseId = request.useCaseId,
                presetId = request.preset.id.value,
                presetRevision = request.preset.version,
            ),
            executionEnvironment(applicationId),
        )
        return when (resolution) {
            is HostExecutionResolution.Failure -> ConsumerActivationResult.Rejected(
                resolution.code.toConsumerFailure(),
            )

            is HostExecutionResolution.Success -> activateResolved(ownerId, applicationId, request, resolution)
        }
    }

    override fun deactivate(ownerId: String, applicationId: ApplicationId, activationId: ConsumerActivationId): ConsumerDeactivationResult {
        val runtimeActivationId = UseCaseActivationId(activationId.value)
        val released = runtimeControl.activationResidency.release(runtimeActivationId, ActivationOwnerId(ownerId))
        return when (released) {
            is ActivationResidencyResult.Failure -> when (released.leaseFailure) {
                ActivationLeaseFailure.NOT_FOUND -> ConsumerDeactivationResult.Released

                ActivationLeaseFailure.NOT_OWNED -> ConsumerDeactivationResult.Rejected(
                    failure(ConsumerControlPlaneErrorCode.INVALID_REQUEST, "Activation is owned by another connection"),
                )

                else -> ConsumerDeactivationResult.Rejected(released.toConsumerFailure())
            }

            is ActivationResidencyResult.Success -> {
                val releasedLease = released.value.releasedLeases.single()
                if (releasedLease.applicationId != applicationId) {
                    return ConsumerDeactivationResult.Rejected(
                        failure(ConsumerControlPlaneErrorCode.INVALID_REQUEST, "Activation belongs to another application"),
                    )
                }
                runtimeControl.removeActivationBinding(runtimeActivationId)
                released.value.warmRetentionByModelMs.forEach(onWarmRetention)
                ConsumerDeactivationResult.Released
            }
        }
    }

    override fun releaseAll(ownerId: String, applicationId: ApplicationId) {
        val released = runtimeControl.activationResidency.releaseAll(ActivationOwnerId(ownerId))
        released.releasedLeases
            .filter { it.applicationId == applicationId }
            .forEach { runtimeControl.removeActivationBinding(it.activationId) }
        released.warmRetentionByModelMs.forEach(onWarmRetention)
    }

    private fun activateResolved(
        ownerId: String,
        applicationId: ApplicationId,
        request: ConsumerActivationRequest,
        resolution: HostExecutionResolution.Success,
    ): ConsumerActivationResult {
        val execution = resolution.execution
        val installed = modelStore.find(execution.modelDigest)
        val imported = installed?.let { importedModel(it.digest, it.sizeBytes) }
        val baseRuntimeResolved = imported?.let { HarnessSharedRuntimeBindings.resolveOmbra(it, applicationId) }
        val preparationFailure = activationPreparationFailure(
            staleRevision = execution.useCaseRevision != request.useCaseRevision || execution.bindingRevision != request.bindingRevision,
            modelInstalled = installed != null,
            modelImported = imported != null,
            expectedModelDigest = execution.modelDigest,
            runtimeModelDigest = baseRuntimeResolved?.model?.artifact?.digest,
        )
        if (preparationFailure != null) {
            return ConsumerActivationResult.Rejected(preparationFailure)
        }
        val runtimeResolved = runCatching {
            requireNotNull(baseRuntimeResolved).withActivatedPresetAlias(
                publicPreset = request.preset,
                canonicalInferencePreset = execution.inferencePreset,
                generationOverrides = execution.generationOverrides,
            )
        }.getOrElse {
            return ConsumerActivationResult.Rejected(
                failure(
                    ConsumerControlPlaneErrorCode.RUNTIME_FAILURE,
                    "Resolved inference preset is unavailable to the runtime",
                ),
            )
        }
        val acquired = runtimeControl.activationResidency.acquireExclusiveUseCase(
            request = UseCaseActivationRequest(
                ownerId = ActivationOwnerId(ownerId),
                applicationId = applicationId,
                useCaseId = request.useCaseId,
                preset = request.preset,
                modelDigest = execution.modelDigest,
                acquiredAtEpochMs = epochClock(),
                useCaseRevision = execution.useCaseRevision,
                bindingRevision = execution.bindingRevision,
            ),
            retainModelWarmMs = execution.cachePolicy.retainModelWarmMs,
        )
        return when (acquired) {
            is ActivationResidencyResult.Failure -> ConsumerActivationResult.Rejected(acquired.toConsumerFailure())

            is ActivationResidencyResult.Success ->
                activateRuntimeBinding(acquired.value, applicationId, request, runtimeResolved)
        }
    }

    private fun activateRuntimeBinding(
        lease: UseCaseActivationLease,
        applicationId: ApplicationId,
        request: ConsumerActivationRequest,
        runtimeResolved: ResolvedUseCase,
    ): ConsumerActivationResult {
        val installedBinding = runCatching {
            runtimeControl.installActivationBinding(
                activationId = lease.activationId,
                applicationId = applicationId,
                useCaseId = request.useCaseId,
                resolved = runtimeResolved,
            )
        }
        if (installedBinding.isFailure) {
            runtimeControl.activationResidency.release(lease.activationId, lease.ownerId)
            return ConsumerActivationResult.Rejected(
                failure(ConsumerControlPlaneErrorCode.RUNTIME_FAILURE, "Unable to bind activated execution"),
            )
        }
        return ConsumerActivationResult.Activated(
            ConsumerActivation(
                activationId = ConsumerActivationId(lease.activationId.value),
                useCaseId = lease.useCaseId,
                useCaseRevision = lease.useCaseRevision,
                bindingRevision = lease.bindingRevision,
                preset = lease.preset,
            ),
        )
    }

    private fun executionEnvironment(applicationId: ApplicationId): HostExecutionEnvironment {
        val installed = modelStore.snapshot().entries
        val profiles = installed.mapNotNull { stored ->
            importedModel(stored.digest, stored.sizeBytes)?.let { model ->
                runCatching { HarnessSharedRuntimeBindings.resolveOmbra(model, applicationId).model }.getOrNull()
            }
        }
        return HostExecutionEnvironment(
            modelProfiles = profiles,
            installedModelDigests = installed.map { it.digest }.toSet(),
            backendId = LLAMA_CPP_BACKEND_ID,
            backendRevision = Qwen35RuntimeTuningProfiles.LLAMA_CPP_REVISION,
        )
    }

    private fun importedModel(digest: ModelDigest, sizeBytes: Long): ImportedPhoneModel? {
        val release = CuratedModelCatalog.releases.singleOrNull { candidate ->
            candidate.artifact.digest == digest && candidate.artifact.sizeBytes == sizeBytes
        } ?: return null
        return ImportedPhoneModel(
            digest = digest,
            fileName = release.artifact.fileName,
            sizeBytes = sizeBytes,
            architecture = release.artifact.architecture,
            quantization = release.artifact.quantization,
        )
    }
}

private fun activationPreparationFailure(
    staleRevision: Boolean,
    modelInstalled: Boolean,
    modelImported: Boolean,
    expectedModelDigest: ModelDigest,
    runtimeModelDigest: ModelDigest?,
): ConsumerControlPlaneFailure? = when {
    staleRevision -> failure(
        ConsumerControlPlaneErrorCode.STALE_REVISION,
        "Consumer configuration changed; refresh assignments",
    )

    !modelInstalled -> failure(
        ConsumerControlPlaneErrorCode.MODEL_UNAVAILABLE,
        "Required local model is unavailable",
    )

    !modelImported -> failure(
        ConsumerControlPlaneErrorCode.MODEL_UNAVAILABLE,
        "Required local model is unsupported",
    )

    runtimeModelDigest != expectedModelDigest -> failure(
        ConsumerControlPlaneErrorCode.RUNTIME_FAILURE,
        "Resolved model identity mismatch",
    )

    else -> null
}

private fun AssignedUseCaseDiscoveryFailure.toConsumerFailure(): ConsumerControlPlaneFailure = when (this) {
    AssignedUseCaseDiscoveryFailure.UNKNOWN_APPLICATION ->
        failure(ConsumerControlPlaneErrorCode.UNKNOWN_APPLICATION, "Consumer application is unknown")

    AssignedUseCaseDiscoveryFailure.APPLICATION_NOT_AUTHORIZED ->
        failure(ConsumerControlPlaneErrorCode.APPLICATION_NOT_AUTHORIZED, "Consumer application is not authorized")
}

private fun PublishedPresetDiscoveryFailure.toConsumerFailure(): ConsumerControlPlaneFailure = when (this) {
    PublishedPresetDiscoveryFailure.UNKNOWN_APPLICATION ->
        failure(ConsumerControlPlaneErrorCode.UNKNOWN_APPLICATION, "Consumer application is unknown")

    PublishedPresetDiscoveryFailure.APPLICATION_NOT_AUTHORIZED ->
        failure(ConsumerControlPlaneErrorCode.APPLICATION_NOT_AUTHORIZED, "Consumer application is not authorized")

    PublishedPresetDiscoveryFailure.USE_CASE_NOT_ASSIGNED ->
        failure(ConsumerControlPlaneErrorCode.USE_CASE_NOT_ASSIGNED, "Use case is not assigned")
}

private fun HostExecutionFailureCode.toConsumerFailure(): ConsumerControlPlaneFailure = when (this) {
    HostExecutionFailureCode.UNKNOWN_APPLICATION ->
        failure(ConsumerControlPlaneErrorCode.UNKNOWN_APPLICATION, "Consumer application is unknown")

    HostExecutionFailureCode.APPLICATION_NOT_AUTHORIZED ->
        failure(ConsumerControlPlaneErrorCode.APPLICATION_NOT_AUTHORIZED, "Consumer application is not authorized")

    HostExecutionFailureCode.USE_CASE_NOT_BOUND,
    HostExecutionFailureCode.USE_CASE_DISABLED,
    -> failure(ConsumerControlPlaneErrorCode.USE_CASE_NOT_ASSIGNED, "Use case is not assigned")

    HostExecutionFailureCode.DEFAULT_PRESET_NOT_CONFIGURED ->
        failure(ConsumerControlPlaneErrorCode.CONFIGURATION_REQUIRED, "Harness configuration is required")

    HostExecutionFailureCode.PRESET_NOT_EXPOSED ->
        failure(ConsumerControlPlaneErrorCode.PRESET_NOT_EXPOSED, "Preset is not available to this consumer")

    HostExecutionFailureCode.PRESET_UNAVAILABLE,
    HostExecutionFailureCode.STALE_PRESET_REVISION,
    -> failure(ConsumerControlPlaneErrorCode.STALE_REVISION, "Consumer configuration changed; refresh assignments")

    HostExecutionFailureCode.MODEL_PROFILE_MISSING,
    HostExecutionFailureCode.MODEL_NOT_INSTALLED,
    HostExecutionFailureCode.MODEL_INCOMPATIBLE,
    HostExecutionFailureCode.NO_COMPATIBLE_MODEL,
    -> failure(ConsumerControlPlaneErrorCode.MODEL_UNAVAILABLE, "Required local model is unavailable")
}

private fun ActivationResidencyResult.Failure.toConsumerFailure(): ConsumerControlPlaneFailure = when (reason) {
    ActivationResidencyFailure.MODEL_CONFLICT ->
        failure(ConsumerControlPlaneErrorCode.MODEL_CONFLICT, "Another active use case protects a different local model")

    ActivationResidencyFailure.USE_CASE_ALREADY_ACTIVE ->
        failure(ConsumerControlPlaneErrorCode.ACTIVATION_ALREADY_ACTIVE, "Use case is already active for this connection")

    ActivationResidencyFailure.LEASE_REJECTED ->
        failure(ConsumerControlPlaneErrorCode.RUNTIME_FAILURE, "Unable to acquire local-AI activation")
}

private fun failure(code: ConsumerControlPlaneErrorCode, message: String) = ConsumerControlPlaneFailure(code, message)
