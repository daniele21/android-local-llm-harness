package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.StoredModel

interface SupportedEvaluationModelSource {
    fun find(modelProfileId: String): GgufModelProfile?
}

class FixedSupportedEvaluationModelSource(profiles: Collection<GgufModelProfile>) : SupportedEvaluationModelSource {
    private val profilesById: Map<String, GgufModelProfile>

    init {
        require(profiles.map { it.id }.distinct().size == profiles.size) {
            "Supported evaluation model profile IDs must be unique"
        }
        profilesById = profiles.associateBy { it.id }
    }

    override fun find(modelProfileId: String): GgufModelProfile? = profilesById[modelProfileId]
}

data class ResolvedEvaluationModel(val identity: EvaluationModelIdentity, val profile: GgufModelProfile, val storedModel: StoredModel)

sealed interface EvaluationModelResolution {
    data class Resolved(val model: ResolvedEvaluationModel) : EvaluationModelResolution

    data class Rejected(val failure: EvaluationFailure) : EvaluationModelResolution
}

class ControlledEvaluationModelResolver(private val supportedModels: SupportedEvaluationModelSource, private val modelStore: ModelStore) {
    fun resolve(identity: EvaluationModelIdentity): EvaluationModelResolution = when (
        val profile = supportedModels.find(identity.modelProfileId)
    ) {
        null -> rejected(EvaluationFailureCode.MODEL_UNSUPPORTED)
        else -> resolveSupported(identity, profile)
    }

    private fun resolveSupported(identity: EvaluationModelIdentity, profile: GgufModelProfile): EvaluationModelResolution {
        val stored = modelStore.find(identity.artifactDigest)
        return when {
            profile.artifact.digest != identity.artifactDigest -> rejected(EvaluationFailureCode.MODEL_UNSUPPORTED)

            identity.quantization != null && profile.artifact.quantization != identity.quantization -> {
                rejected(EvaluationFailureCode.MODEL_UNSUPPORTED)
            }

            stored == null || !stored.verified || stored.digest != identity.artifactDigest -> {
                rejected(EvaluationFailureCode.MODEL_NOT_INSTALLED)
            }

            else -> EvaluationModelResolution.Resolved(
                ResolvedEvaluationModel(
                    identity = identity,
                    profile = profile,
                    storedModel = stored,
                ),
            )
        }
    }

    private fun rejected(code: EvaluationFailureCode): EvaluationModelResolution.Rejected = EvaluationModelResolution.Rejected(
        EvaluationFailure(
            stage = EvaluationFailureStage.PREFLIGHT,
            code = code,
        ),
    )
}
