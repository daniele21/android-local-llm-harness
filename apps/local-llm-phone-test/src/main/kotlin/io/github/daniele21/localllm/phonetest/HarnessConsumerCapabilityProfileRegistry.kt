package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.HostExecutionEnvironment
import io.github.daniele21.localllm.models.HostExecutionFailureCode
import io.github.daniele21.localllm.models.HostExecutionRequest
import io.github.daniele21.localllm.models.HostExecutionResolution
import io.github.daniele21.localllm.models.HostExecutionResolver
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.Qwen35RuntimeTuningProfiles
import io.github.daniele21.localllm.models.ResolvedHostExecution
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.store.ModelStore

/**
 * Capability-discovery view of external Consumer execution.
 *
 * Runtime execution still resolves external Consumers only through an active control-plane activation in
 * [HarnessPhoneBindingRegistry]. Capability discovery instead needs a side-effect-free view before activation,
 * so it resolves the currently authorized/bound/default execution from the persisted Control Plane.
 */
internal class HarnessConsumerCapabilityProfileRegistry(
    private val activeBindings: HarnessPhoneBindingRegistry,
    private val configuredResolver: (ApplicationId, UseCaseId) -> ResolvedUseCase,
) : ModelProfileRegistry {
    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase =
        activeBindings.activeResolved(applicationId, useCaseId)
            ?: configuredResolver(applicationId, useCaseId)
}

/**
 * Side-effect-free adapter from persisted Host Control Plane state to a Consumer-safe runtime profile view.
 *
 * Capability discovery must resolve configuration even when its model bytes are currently absent. Actual inventory
 * readiness remains owned by [io.github.daniele21.localllm.runtime.ConsumerCapabilityPolicyService], while activation
 * and runtime setup continue to require a present model through the normal Control Plane path.
 */
internal class HarnessConfiguredConsumerProfileResolver(private val store: HostControlPlaneStore, private val modelStore: ModelStore) {
    private val resolver = HostExecutionResolver(store)

    fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
        val configuredModels = configuredModels(applicationId, useCaseId)
        val execution = resolveExecution(applicationId, useCaseId, configuredModels)
        val model = requireNotNull(configuredModels.singleOrNull { it.digest == execution.modelDigest }) {
            "Configured Consumer model is unsupported"
        }
        val resolved = HarnessSharedRuntimeBindings.resolveConsumerUseCase(model, applicationId, useCaseId)
        return resolved.withActivatedPresetAlias(
            publicPreset = InferencePresetRef(InferencePresetId(execution.presetId), execution.presetRevision),
            canonicalInferencePreset = execution.inferencePreset,
            generationOverrides = execution.generationOverrides,
        )
    }

    private fun resolveExecution(
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        configuredModels: List<ImportedPhoneModel>,
    ): ResolvedHostExecution {
        val request = HostExecutionRequest(applicationId = applicationId, useCaseId = useCaseId)
        val installed = modelStore.snapshot().entries
        val installedModels = installed.mapNotNull { stored -> importedPhoneModel(stored.digest, stored.sizeBytes) }
        val installedResolution = resolver.resolve(
            request,
            executionEnvironment(applicationId, useCaseId, installedModels, installed.map { it.digest }.toSet()),
        )
        val resolution = if (
            installedResolution is HostExecutionResolution.Failure &&
            installedResolution.code in MODEL_INVENTORY_FAILURES
        ) {
            resolver.resolve(
                request,
                executionEnvironment(
                    applicationId = applicationId,
                    useCaseId = useCaseId,
                    models = configuredModels,
                    installedModelDigests = configuredModels.map(ImportedPhoneModel::digest).toSet(),
                ),
            )
        } else {
            installedResolution
        }
        return when (resolution) {
            is HostExecutionResolution.Success -> resolution.execution

            is HostExecutionResolution.Failure -> error(
                "Configured Consumer execution is unavailable: ${resolution.code}",
            )
        }
    }

    private fun configuredModels(applicationId: ApplicationId, useCaseId: UseCaseId): List<ImportedPhoneModel> =
        CuratedModelCatalog.releases.mapNotNull { release ->
            importedPhoneModel(release.artifact.digest, release.artifact.sizeBytes)?.takeIf { model ->
                runCatching {
                    HarnessSharedRuntimeBindings.resolveConsumerUseCase(model, applicationId, useCaseId)
                }.isSuccess
            }
        }

    private fun executionEnvironment(
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        models: List<ImportedPhoneModel>,
        installedModelDigests: Set<ModelDigest>,
    ): HostExecutionEnvironment {
        val profiles = models.mapNotNull { model ->
            runCatching {
                HarnessSharedRuntimeBindings.resolveConsumerUseCase(model, applicationId, useCaseId).model
            }.getOrNull()
        }
        return HostExecutionEnvironment(
            modelProfiles = profiles,
            installedModelDigests = installedModelDigests,
            backendId = "llama.cpp",
            backendRevision = Qwen35RuntimeTuningProfiles.LLAMA_CPP_REVISION,
        )
    }

    private companion object {
        val MODEL_INVENTORY_FAILURES = setOf(
            HostExecutionFailureCode.MODEL_PROFILE_MISSING,
            HostExecutionFailureCode.MODEL_NOT_INSTALLED,
            HostExecutionFailureCode.NO_COMPATIBLE_MODEL,
        )
    }
}
