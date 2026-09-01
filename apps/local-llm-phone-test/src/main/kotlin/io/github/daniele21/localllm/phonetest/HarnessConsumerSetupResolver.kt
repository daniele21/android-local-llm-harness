package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneErrorCode
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneFailure
import io.github.daniele21.localllm.contracts.ConsumerGenerationConfiguration
import io.github.daniele21.localllm.contracts.ConsumerResolvedSetup
import io.github.daniele21.localllm.contracts.ConsumerSetupResolutionRequest
import io.github.daniele21.localllm.contracts.ConsumerSetupResolutionResult
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SeedPolicy
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.ResolvedHostExecution
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.withPresetOverrides
import io.github.daniele21.localllm.store.ModelStore

internal class HarnessConsumerSetupResolver(
    private val modelStore: ModelStore,
) {
    fun resolve(
        applicationId: ApplicationId,
        request: ConsumerSetupResolutionRequest,
        execution: ResolvedHostExecution,
    ): ConsumerSetupResolutionResult =
        if (execution.useCaseRevision != request.useCaseRevision || execution.bindingRevision != request.bindingRevision) {
            rejected(
                ConsumerControlPlaneErrorCode.STALE_REVISION,
                "Consumer configuration changed; refresh assignments",
            )
        } else {
            modelStore.find(execution.modelDigest)?.let { stored ->
                resolveInstalled(applicationId, execution, stored.digest, stored.sizeBytes)
            } ?: rejected(
                ConsumerControlPlaneErrorCode.MODEL_UNAVAILABLE,
                "Required local model is unavailable",
            )
        }

    private fun resolveInstalled(
        applicationId: ApplicationId,
        execution: ResolvedHostExecution,
        digest: ModelDigest,
        sizeBytes: Long,
    ): ConsumerSetupResolutionResult =
        importedPhoneModel(digest, sizeBytes)?.let { model ->
            resolveRuntime(applicationId, execution, model)
        } ?: rejected(
            ConsumerControlPlaneErrorCode.MODEL_UNAVAILABLE,
            "Required local model is unsupported",
        )

    private fun resolveRuntime(
        applicationId: ApplicationId,
        execution: ResolvedHostExecution,
        model: ImportedPhoneModel,
    ): ConsumerSetupResolutionResult {
        val runtimeResolved = runCatching { HarnessSharedRuntimeBindings.resolveOmbra(model, applicationId) }.getOrNull()
        return when {
            runtimeResolved == null -> rejected(
                ConsumerControlPlaneErrorCode.RUNTIME_FAILURE,
                "Resolved setup is unavailable to the runtime",
            )

            runtimeResolved.model.id != execution.modelProfileId ||
                runtimeResolved.model.artifact.digest != execution.modelDigest -> rejected(
                ConsumerControlPlaneErrorCode.RUNTIME_FAILURE,
                "Resolved model identity mismatch",
            )

            else -> runtimeResolved.effectiveGeneration(execution)?.let { generation ->
                ConsumerSetupResolutionResult.Resolved(
                    ConsumerResolvedSetup(
                        useCaseId = execution.useCaseId,
                        useCaseRevision = execution.useCaseRevision,
                        bindingRevision = execution.bindingRevision,
                        preset = InferencePresetRef(InferencePresetId(execution.presetId), execution.presetRevision),
                        modelProfileId = execution.modelProfileId,
                        contextTokens = execution.contextTokens,
                        generation = generation.toConsumerGenerationConfiguration(),
                    ),
                )
            } ?: rejected(
                ConsumerControlPlaneErrorCode.RUNTIME_FAILURE,
                "Resolved inference preset is unavailable to the runtime",
            )
        }
    }
}

internal fun importedPhoneModel(digest: ModelDigest, sizeBytes: Long): ImportedPhoneModel? =
    CuratedModelCatalog.releases.singleOrNull { candidate ->
        candidate.artifact.digest == digest && candidate.artifact.sizeBytes == sizeBytes
    }?.let { release ->
        ImportedPhoneModel(
            digest = digest,
            fileName = release.artifact.fileName,
            sizeBytes = sizeBytes,
            architecture = release.artifact.architecture,
            quantization = release.artifact.quantization,
        )
    }

private fun ResolvedUseCase.effectiveGeneration(execution: ResolvedHostExecution): GenerationDefaults? =
    useCase.presets.singleOrNull { it.ref == execution.inferencePreset }?.generation?.withPresetOverrides(execution.generationOverrides)

private fun GenerationDefaults.toConsumerGenerationConfiguration() = ConsumerGenerationConfiguration(
    maxOutputTokens = maxOutputTokens,
    temperature = temperature,
    topP = topP,
    topK = topK,
    minP = minP,
    presencePenalty = presencePenalty,
    repeatPenalty = repeatPenalty,
    repeatLastN = repeatLastN,
    thinkingMode = thinkingMode,
    seedPolicy = when (seedPolicy) {
        SeedPolicy.Random -> SeedPolicyType.RANDOM
        is SeedPolicy.Fixed -> SeedPolicyType.FIXED
    },
)

private fun rejected(code: ConsumerControlPlaneErrorCode, message: String) = ConsumerSetupResolutionResult.Rejected(
    ConsumerControlPlaneFailure(code, message),
)
