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
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.integration.servicehost.ConsumerControlPlaneHost
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.ApplicationUseCaseBinding
import io.github.daniele21.localllm.models.AssignedUseCaseDiscovery
import io.github.daniele21.localllm.models.AssignedUseCaseDiscoveryFailure
import io.github.daniele21.localllm.models.AssignedUseCaseDiscoveryResult
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.HostExecutionEnvironment
import io.github.daniele21.localllm.models.HostExecutionFailureCode
import io.github.daniele21.localllm.models.HostExecutionRequest
import io.github.daniele21.localllm.models.HostExecutionResolution
import io.github.daniele21.localllm.models.HostExecutionResolver
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetExecutionPolicy
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.PublishedPresetDiscovery
import io.github.daniele21.localllm.models.PublishedPresetDiscoveryFailure
import io.github.daniele21.localllm.models.PublishedPresetDiscoveryResult
import io.github.daniele21.localllm.models.Qwen35RuntimeTuningProfiles
import io.github.daniele21.localllm.models.RegisteredApplication
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseDefinition
import io.github.daniele21.localllm.models.UseCaseDefinitionState
import io.github.daniele21.localllm.models.UseCasePresetDefinition
import io.github.daniele21.localllm.models.UseCaseRequirements
import io.github.daniele21.localllm.runtime.ActivationLeaseFailure
import io.github.daniele21.localllm.runtime.ActivationOwnerId
import io.github.daniele21.localllm.runtime.ActivationResidencyFailure
import io.github.daniele21.localllm.runtime.ActivationResidencyResult
import io.github.daniele21.localllm.runtime.UseCaseActivationId
import io.github.daniele21.localllm.runtime.UseCaseActivationLease
import io.github.daniele21.localllm.runtime.UseCaseActivationRequest
import io.github.daniele21.localllm.store.ModelStore

internal class HarnessConsumerControlPlaneHost(
    private val store: HostControlPlaneStore,
    private val modelStore: ModelStore,
    private val runtimeGraph: HarnessRuntimeGraph,
    private val applicationSeeds: List<RegisteredApplication>,
    private val epochClock: () -> Long = System::currentTimeMillis,
    private val onWarmRetention: (ModelDigest, Long) -> Unit = { _, _ -> },
) : ConsumerControlPlaneHost {
    private val resolver = HostExecutionResolver(store)
    private val useCaseDiscovery = AssignedUseCaseDiscovery(store)
    private val presetDiscovery = PublishedPresetDiscovery(store)

    override fun assignedUseCases(applicationId: ApplicationId): ConsumerAssignedUseCasesResult {
        ensureSeeded()
        return when (val result = useCaseDiscovery.discover(applicationId)) {
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
    }

    override fun publishedPresets(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerPublishedPresetsResult {
        ensureSeeded()
        return when (val result = presetDiscovery.discover(applicationId, useCaseId)) {
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
    }

    override fun activate(ownerId: String, applicationId: ApplicationId, request: ConsumerActivationRequest): ConsumerActivationResult {
        ensureSeeded()
        val resolution = resolver.resolve(
            HostExecutionRequest(
                applicationId = applicationId,
                useCaseId = request.useCaseId,
                presetId = request.preset.id.value,
                presetRevision = request.preset.version,
            ),
            executionEnvironment(applicationId),
        )
        val execution = when (resolution) {
            is HostExecutionResolution.Failure -> return ConsumerActivationResult.Rejected(
                resolution.code.toConsumerFailure(),
            )

            is HostExecutionResolution.Success -> resolution.execution
        }
        if (execution.useCaseRevision != request.useCaseRevision || execution.bindingRevision != request.bindingRevision) {
            return ConsumerActivationResult.Rejected(
                failure(ConsumerControlPlaneErrorCode.STALE_REVISION, "Consumer configuration changed; refresh assignments"),
            )
        }

        val installed = modelStore.find(execution.modelDigest)
            ?.takeIf { it.verified }
            ?: return ConsumerActivationResult.Rejected(
                failure(ConsumerControlPlaneErrorCode.MODEL_UNAVAILABLE, "Required local model is unavailable"),
            )
        val imported = importedModel(installed.digest, installed.sizeBytes)
            ?: return ConsumerActivationResult.Rejected(
                failure(ConsumerControlPlaneErrorCode.MODEL_UNAVAILABLE, "Required local model is unsupported"),
            )
        val runtimeResolved = HarnessSharedRuntimeBindings.resolveOmbra(imported, applicationId)
        if (runtimeResolved.model.artifact.digest != execution.modelDigest) {
            return ConsumerActivationResult.Rejected(
                failure(ConsumerControlPlaneErrorCode.RUNTIME_FAILURE, "Resolved model identity mismatch"),
            )
        }

        val acquired = runtimeGraph.activationResidency.acquireExclusiveUseCase(
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

            is ActivationResidencyResult.Success -> activateRuntimeBinding(acquired.value, applicationId, request, runtimeResolved)
        }
    }

    override fun deactivate(ownerId: String, applicationId: ApplicationId, activationId: ConsumerActivationId): ConsumerDeactivationResult {
        val runtimeActivationId = UseCaseActivationId(activationId.value)
        val released = runtimeGraph.activationResidency.release(runtimeActivationId, ActivationOwnerId(ownerId))
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
                runtimeGraph.removeActivationBinding(runtimeActivationId)
                released.value.warmRetentionByModelMs.forEach(onWarmRetention)
                ConsumerDeactivationResult.Released
            }
        }
    }

    override fun releaseAll(ownerId: String, applicationId: ApplicationId) {
        val released = runtimeGraph.activationResidency.releaseAll(ActivationOwnerId(ownerId))
        released.releasedLeases
            .filter { it.applicationId == applicationId }
            .forEach { runtimeGraph.removeActivationBinding(it.activationId) }
        released.warmRetentionByModelMs.forEach(onWarmRetention)
    }

    private fun activateRuntimeBinding(
        lease: UseCaseActivationLease,
        applicationId: ApplicationId,
        request: ConsumerActivationRequest,
        runtimeResolved: ResolvedUseCase,
    ): ConsumerActivationResult {
        val installedBinding = runCatching {
            runtimeGraph.installActivationBinding(
                activationId = lease.activationId,
                applicationId = applicationId,
                useCaseId = request.useCaseId,
                resolved = runtimeResolved,
            )
        }
        if (installedBinding.isFailure) {
            runtimeGraph.activationResidency.release(lease.activationId, lease.ownerId)
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

    @Synchronized
    private fun ensureSeeded() {
        val current = store.snapshot()
        if (current != HostControlPlaneState()) return
        val seeds = applicationSeeds.distinctBy(RegisteredApplication::applicationId)
        if (seeds.isEmpty()) return
        val bindings = seeds.map { application ->
            ApplicationUseCaseBinding(
                bindingId = "seed-${application.applicationId.value}-${HarnessSharedRuntimeBindings.ombraUseCaseId.value}",
                applicationId = application.applicationId,
                useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
                revision = SEED_REVISION,
                enabled = true,
                isDefault = true,
            )
        }
        store.replace(
            HostControlPlaneState(
                applications = seeds,
                useCases = listOf(seedUseCase()),
                presets = listOf(seedPreset()),
                bindings = bindings,
                exposures = bindings.map { binding ->
                    StoredPresetExposure(
                        bindingId = binding.bindingId,
                        bindingRevision = binding.revision,
                        presetId = HarnessSharedRuntimeBindings.ombraDefaultPreset.id.value,
                        presetRevision = HarnessSharedRuntimeBindings.ombraDefaultPreset.version,
                        isDefault = true,
                    )
                },
            ),
        )
    }

    private fun executionEnvironment(applicationId: ApplicationId): HostExecutionEnvironment {
        val installed = modelStore.snapshot().entries.filter { it.verified }
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

    private fun seedUseCase() = UseCaseDefinition(
        useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
        displayName = "Document PII detection",
        description = "Detect configured PII locally from document text",
        requirements = UseCaseRequirements(
            outputMode = OutputMode.JSON_SCHEMA,
            sessionKind = SessionKind.STATELESS,
            reasoningSupported = false,
            minimumContextTokens = OMBRA_MINIMUM_CONTEXT_TOKENS,
            maxInputCharacters = OMBRA_MAX_INPUT_CHARACTERS,
            maxJsonSchemaCharacters = OMBRA_MAX_SCHEMA_CHARACTERS,
        ),
        state = UseCaseDefinitionState.ACTIVE,
        revision = SEED_REVISION,
    )

    private fun seedPreset() = UseCasePresetDefinition(
        useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
        metadata = PresetConsumerMetadata(
            presetId = HarnessSharedRuntimeBindings.ombraDefaultPreset.id.value,
            revision = HarnessSharedRuntimeBindings.ombraDefaultPreset.version,
            displayName = "Balanced local PII",
            description = "Automatic local Qwen3.5 selection for structured PII detection",
        ),
        creationSource = PresetCreationSource.SUGGESTED,
        state = PresetLifecycleState.PUBLISHED,
        execution = PresetExecutionPolicy(
            modelProfileId = null,
            inferencePreset = HarnessSharedRuntimeBindings.ombraDefaultPreset,
            contextTokens = OMBRA_MINIMUM_CONTEXT_TOKENS,
            cachePolicy = UseCaseCachePolicy(
                retainModelWarmMs = MIGRATION_WARM_RETENTION_MS,
                reuseStatelessContext = false,
                enablePrefixSnapshot = false,
                enableDeterministicResultCache = false,
            ),
        ),
    )

    private companion object {
        const val SEED_REVISION = 1
        const val OMBRA_MINIMUM_CONTEXT_TOKENS = 4_096
        const val OMBRA_MAX_INPUT_CHARACTERS = 12_000
        const val OMBRA_MAX_SCHEMA_CHARACTERS = 4_096
        const val MIGRATION_WARM_RETENTION_MS = 60_000L
        const val LLAMA_CPP_BACKEND_ID = "llama.cpp"
    }
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
