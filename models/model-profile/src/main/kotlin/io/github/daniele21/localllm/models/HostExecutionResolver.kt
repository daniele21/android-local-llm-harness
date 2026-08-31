package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId

data class HostExecutionRequest(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val presetId: String? = null,
    val presetRevision: Int? = null,
) {
    init {
        require(presetId == null || presetId.isNotBlank()) { "Requested preset ID must not be blank" }
        require(presetRevision == null || presetRevision > 0) { "Requested preset revision must be positive" }
        require(presetRevision == null || presetId != null) { "Requested preset revision requires a preset ID" }
    }
}

data class HostExecutionEnvironment(
    val modelProfiles: Collection<GgufModelProfile>,
    val installedModelDigests: Set<ModelDigest>,
    val backendId: String,
    val backendRevision: String,
) {
    init {
        require(backendId.isNotBlank()) { "Backend ID must not be blank" }
        require(backendRevision.isNotBlank()) { "Backend revision must not be blank" }
        require(modelProfiles.distinctBy(GgufModelProfile::id).size == modelProfiles.size) {
            "Model profile IDs must be unique"
        }
    }
}

enum class HostExecutionFailureCode {
    UNKNOWN_APPLICATION,
    APPLICATION_NOT_AUTHORIZED,
    USE_CASE_NOT_BOUND,
    USE_CASE_DISABLED,
    DEFAULT_PRESET_NOT_CONFIGURED,
    PRESET_NOT_EXPOSED,
    STALE_PRESET_REVISION,
    PRESET_UNAVAILABLE,
    MODEL_PROFILE_MISSING,
    MODEL_NOT_INSTALLED,
    MODEL_INCOMPATIBLE,
    NO_COMPATIBLE_MODEL,
}

enum class ModelCandidateRejectionReason {
    NOT_INSTALLED,
    INSUFFICIENT_CONTEXT,
    REQUIRED_BACKEND_MISMATCH,
    REQUIRED_BACKEND_REVISION_MISMATCH,
    STATELESS_REUSE_UNSUPPORTED,
    PREFIX_SNAPSHOT_UNSUPPORTED,
}

data class ModelCandidateRejection(
    val modelProfileId: String,
    val modelDigest: ModelDigest,
    val reasons: Set<ModelCandidateRejectionReason>,
) {
    init {
        require(modelProfileId.isNotBlank()) { "Rejected model profile ID must not be blank" }
        require(reasons.isNotEmpty()) { "Rejected model candidate must contain at least one reason" }
    }
}

data class HostExecutionResolutionEvidence(
    val bindingId: String? = null,
    val bindingRevision: Int? = null,
    val useCaseRevision: Int? = null,
    val requestedPresetId: String? = null,
    val requestedPresetRevision: Int? = null,
    val exposedPresetRevision: Int? = null,
    val requestedModelProfileId: String? = null,
    val candidateRejections: List<ModelCandidateRejection> = emptyList(),
)

data class ResolvedHostExecution(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val useCaseRevision: Int,
    val bindingId: String,
    val bindingRevision: Int,
    val presetId: String,
    val presetRevision: Int,
    val modelProfileId: String,
    val modelDigest: ModelDigest,
    val inferencePreset: InferencePresetRef,
    val contextTokens: Int,
    val cachePolicy: UseCaseCachePolicy,
    val generationOverrides: PresetGenerationOverrides?,
    val evidence: HostExecutionResolutionEvidence,
) {
    init {
        require(useCaseRevision > 0) { "Resolved use-case revision must be positive" }
        require(bindingId.isNotBlank()) { "Resolved binding ID must not be blank" }
        require(bindingRevision > 0) { "Resolved binding revision must be positive" }
        require(presetId.isNotBlank()) { "Resolved preset ID must not be blank" }
        require(presetRevision > 0) { "Resolved preset revision must be positive" }
        require(modelProfileId.isNotBlank()) { "Resolved model profile ID must not be blank" }
        require(contextTokens > 0) { "Resolved context tokens must be positive" }
    }
}

sealed interface HostExecutionResolution {
    data class Success(val execution: ResolvedHostExecution) : HostExecutionResolution

    data class Failure(
        val code: HostExecutionFailureCode,
        val detail: String,
        val applicationId: ApplicationId,
        val useCaseId: UseCaseId,
        val evidence: HostExecutionResolutionEvidence,
    ) : HostExecutionResolution.FailureMarker {
        init {
            require(detail.isNotBlank()) { "Resolution failure detail must not be blank" }
        }
    }

    private interface FailureMarker : HostExecutionResolution
}

class HostExecutionResolver(private val store: HostControlPlaneStore) {
    @Suppress("ReturnCount", "LongMethod")
    fun resolve(request: HostExecutionRequest, environment: HostExecutionEnvironment): HostExecutionResolution {
        val state = store.snapshot()
        val application = state.applications.firstOrNull { it.applicationId == request.applicationId }
            ?: return failure(
                request,
                HostExecutionFailureCode.UNKNOWN_APPLICATION,
                "Application is not registered in the Harness control plane",
            )
        if (application.state != ApplicationRegistrationState.AUTHORIZED) {
            return failure(
                request,
                HostExecutionFailureCode.APPLICATION_NOT_AUTHORIZED,
                "Application is registered but not authorized for inference",
            )
        }

        val binding = state.latestBinding(request.applicationId, request.useCaseId)
            ?: return failure(
                request,
                HostExecutionFailureCode.USE_CASE_NOT_BOUND,
                "Use case is not bound to this application",
            )
        val bindingEvidence = HostExecutionResolutionEvidence(
            bindingId = binding.bindingId,
            bindingRevision = binding.revision,
            requestedPresetId = request.presetId,
            requestedPresetRevision = request.presetRevision,
        )
        if (!binding.enabled) {
            return failure(
                request,
                HostExecutionFailureCode.USE_CASE_NOT_BOUND,
                "Latest application/use-case binding revision is disabled",
                bindingEvidence,
            )
        }

        val useCase = state.latestUseCase(request.useCaseId)
            ?: return failure(
                request,
                HostExecutionFailureCode.USE_CASE_NOT_BOUND,
                "Bound use case has no persisted definition",
                bindingEvidence,
            )
        val useCaseEvidence = bindingEvidence.copy(useCaseRevision = useCase.revision)
        if (useCase.state != UseCaseDefinitionState.ACTIVE) {
            return failure(
                request,
                HostExecutionFailureCode.USE_CASE_DISABLED,
                "Latest use-case revision is not active",
                useCaseEvidence,
            )
        }

        val exposure = resolveExposure(state, binding, request, useCaseEvidence)
        if (exposure is HostExecutionResolution.Failure) return exposure
        exposure as StoredPresetExposure

        val preset = state.preset(request.useCaseId, exposure.presetId, exposure.presetRevision)
            ?: return failure(
                request,
                HostExecutionFailureCode.PRESET_UNAVAILABLE,
                "Exposed preset revision is unavailable",
                useCaseEvidence.copy(exposedPresetRevision = exposure.presetRevision),
            )
        val presetEvidence = useCaseEvidence.copy(
            exposedPresetRevision = exposure.presetRevision,
            requestedModelProfileId = preset.execution.modelProfileId,
        )

        val modelResolution = resolveModel(useCase, preset, environment)
        if (modelResolution is ModelResolutionFailure) {
            return failure(
                request,
                modelResolution.code,
                modelResolution.detail,
                presetEvidence.copy(candidateRejections = modelResolution.rejections),
            )
        }
        modelResolution as ModelResolutionSuccess

        return HostExecutionResolution.Success(
            ResolvedHostExecution(
                applicationId = request.applicationId,
                useCaseId = request.useCaseId,
                useCaseRevision = useCase.revision,
                bindingId = binding.bindingId,
                bindingRevision = binding.revision,
                presetId = preset.metadata.presetId,
                presetRevision = preset.metadata.revision,
                modelProfileId = modelResolution.profile.id,
                modelDigest = modelResolution.profile.artifact.digest,
                inferencePreset = preset.execution.inferencePreset,
                contextTokens = effectiveContextTokens(useCase, preset),
                cachePolicy = preset.execution.cachePolicy,
                generationOverrides = preset.execution.generationOverrides,
                evidence = presetEvidence.copy(candidateRejections = modelResolution.rejections),
            ),
        )
    }

    private fun resolveExposure(
        state: HostControlPlaneState,
        binding: ApplicationUseCaseBinding,
        request: HostExecutionRequest,
        evidence: HostExecutionResolutionEvidence,
    ): Any {
        val exposures = state.exposures.filter {
            it.bindingId == binding.bindingId && it.bindingRevision == binding.revision
        }
        if (request.presetId == null) {
            return exposures.singleOrNull(StoredPresetExposure::isDefault)
                ?: failure(
                    request,
                    HostExecutionFailureCode.DEFAULT_PRESET_NOT_CONFIGURED,
                    "Binding revision has no default exposed preset",
                    evidence,
                )
        }

        val matchingId = exposures.filter { it.presetId == request.presetId }
        if (matchingId.isEmpty()) {
            return failure(
                request,
                HostExecutionFailureCode.PRESET_NOT_EXPOSED,
                "Requested preset is not exposed through this binding revision",
                evidence,
            )
        }
        if (request.presetRevision == null) return matchingId.maxBy(StoredPresetExposure::presetRevision)
        return matchingId.firstOrNull { it.presetRevision == request.presetRevision }
            ?: failure(
                request,
                HostExecutionFailureCode.STALE_PRESET_REVISION,
                "Requested preset revision is not the exposed revision",
                evidence.copy(exposedPresetRevision = matchingId.maxOf(StoredPresetExposure::presetRevision)),
            )
    }

    @Suppress("ReturnCount")
    private fun resolveModel(
        useCase: UseCaseDefinition,
        preset: UseCasePresetDefinition,
        environment: HostExecutionEnvironment,
    ): ModelResolution {
        val profiles = environment.modelProfiles.sortedWith(PROFILE_ORDER)
        val requestedProfileId = preset.execution.modelProfileId
        if (requestedProfileId != null) {
            val profile = profiles.firstOrNull { it.id == requestedProfileId }
                ?: return ModelResolutionFailure(
                    HostExecutionFailureCode.MODEL_PROFILE_MISSING,
                    "Preset references a model profile that is not available",
                    emptyList(),
                )
            val rejection = rejectionFor(useCase, preset, profile, environment)
            if (rejection == null) return ModelResolutionSuccess(profile, emptyList())
            val code = if (rejection.reasons == setOf(ModelCandidateRejectionReason.NOT_INSTALLED)) {
                HostExecutionFailureCode.MODEL_NOT_INSTALLED
            } else {
                HostExecutionFailureCode.MODEL_INCOMPATIBLE
            }
            return ModelResolutionFailure(
                code,
                "Preset's assigned model cannot satisfy the current execution requirements",
                listOf(rejection),
            )
        }

        val rejections = mutableListOf<ModelCandidateRejection>()
        profiles.forEach { profile ->
            val rejection = rejectionFor(useCase, preset, profile, environment)
            if (rejection == null) return ModelResolutionSuccess(profile, rejections)
            rejections += rejection
        }
        return ModelResolutionFailure(
            HostExecutionFailureCode.NO_COMPATIBLE_MODEL,
            "No installed model profile can satisfy the preset execution requirements",
            rejections,
        )
    }

    private fun rejectionFor(
        useCase: UseCaseDefinition,
        preset: UseCasePresetDefinition,
        profile: GgufModelProfile,
        environment: HostExecutionEnvironment,
    ): ModelCandidateRejection? {
        val requiredContext = effectiveContextTokens(useCase, preset)
        val reasons = buildSet {
            if (profile.artifact.digest !in environment.installedModelDigests) add(ModelCandidateRejectionReason.NOT_INSTALLED)
            if (profile.contextSize < requiredContext) add(ModelCandidateRejectionReason.INSUFFICIENT_CONTEXT)
            profile.runtimeCapabilities.requiredBackendId?.let { required ->
                if (required != environment.backendId) add(ModelCandidateRejectionReason.REQUIRED_BACKEND_MISMATCH)
            }
            profile.runtimeCapabilities.requiredBackendRevision?.let { required ->
                if (required != environment.backendRevision) add(ModelCandidateRejectionReason.REQUIRED_BACKEND_REVISION_MISMATCH)
            }
            if (preset.execution.cachePolicy.reuseStatelessContext && !profile.runtimeCapabilities.supportsStatelessContextReuse) {
                add(ModelCandidateRejectionReason.STATELESS_REUSE_UNSUPPORTED)
            }
            if (preset.execution.cachePolicy.enablePrefixSnapshot && !profile.runtimeCapabilities.supportsPrefixSnapshot) {
                add(ModelCandidateRejectionReason.PREFIX_SNAPSHOT_UNSUPPORTED)
            }
        }
        return reasons.takeIf(Set<ModelCandidateRejectionReason>::isNotEmpty)?.let {
            ModelCandidateRejection(profile.id, profile.artifact.digest, it)
        }
    }

    private fun effectiveContextTokens(useCase: UseCaseDefinition, preset: UseCasePresetDefinition): Int =
        maxOf(useCase.requirements.minimumContextTokens, preset.execution.contextTokens ?: 0)

    private fun failure(
        request: HostExecutionRequest,
        code: HostExecutionFailureCode,
        detail: String,
        evidence: HostExecutionResolutionEvidence = HostExecutionResolutionEvidence(
            requestedPresetId = request.presetId,
            requestedPresetRevision = request.presetRevision,
        ),
    ): HostExecutionResolution.Failure = HostExecutionResolution.Failure(
        code = code,
        detail = detail,
        applicationId = request.applicationId,
        useCaseId = request.useCaseId,
        evidence = evidence,
    )

    private sealed interface ModelResolution
    private data class ModelResolutionSuccess(val profile: GgufModelProfile, val rejections: List<ModelCandidateRejection>) : ModelResolution
    private data class ModelResolutionFailure(
        val code: HostExecutionFailureCode,
        val detail: String,
        val rejections: List<ModelCandidateRejection>,
    ) : ModelResolution

    private companion object {
        val PROFILE_ORDER = compareBy<GgufModelProfile>(
            { it.artifact.sizeBytes },
            { it.contextSize },
            GgufModelProfile::id,
        )
    }
}
