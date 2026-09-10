package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.HostExecutionEnvironment
import io.github.daniele21.localllm.models.HostExecutionRequest
import io.github.daniele21.localllm.models.HostExecutionResolution
import io.github.daniele21.localllm.models.HostExecutionResolver
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.Qwen35RuntimeTuningProfiles
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

/** Side-effect-free adapter from persisted Host Control Plane state to a Consumer-safe runtime profile view. */
internal class HarnessConfiguredConsumerProfileResolver(
    private val store: HostControlPlaneStore,
    private val modelStore: ModelStore,
) {
    private val resolver = HostExecutionResolver(store)

    fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
        val resolution = resolver.resolve(
            HostExecutionRequest(applicationId = applicationId, useCaseId = useCaseId),
            executionEnvironment(applicationId, useCaseId),
        )
        val execution = when (resolution) {
            is HostExecutionResolution.Success -> resolution.execution

            is HostExecutionResolution.Failure -> error(
                "Configured Consumer execution is unavailable: ${resolution.code}",
            )
        }
        val stored = requireNotNull(modelStore.find(execution.modelDigest)) {
            "Configured Consumer model is unavailable"
        }
        val model = requireNotNull(importedPhoneModel(stored.digest, stored.sizeBytes)) {
            "Configured Consumer model is unsupported"
        }
        val resolved = HarnessSharedRuntimeBindings.resolveConsumerUseCase(model, applicationId, useCaseId)
        return resolved.withActivatedPresetAlias(
            publicPreset = InferencePresetRef(InferencePresetId(execution.presetId), execution.presetRevision),
            canonicalInferencePreset = execution.inferencePreset,
            generationOverrides = execution.generationOverrides,
        )
    }

    private fun executionEnvironment(applicationId: ApplicationId, useCaseId: UseCaseId): HostExecutionEnvironment {
        val installed = modelStore.snapshot().entries
        val profiles = installed.mapNotNull { stored ->
            importedPhoneModel(stored.digest, stored.sizeBytes)?.let { model ->
                runCatching {
                    HarnessSharedRuntimeBindings.resolveConsumerUseCase(model, applicationId, useCaseId).model
                }.getOrNull()
            }
        }
        return HostExecutionEnvironment(
            modelProfiles = profiles,
            installedModelDigests = installed.map { it.digest }.toSet(),
            backendId = "llama.cpp",
            backendRevision = Qwen35RuntimeTuningProfiles.LLAMA_CPP_REVISION,
        )
    }
}
