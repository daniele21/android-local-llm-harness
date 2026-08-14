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

data class ResolvedEvaluationModel(
    val identity: EvaluationModelIdentity,
    val profile: GgufModelProfile,
    val storedModel: StoredModel,
)

sealed interface EvaluationModelResolution {
    data class Resolved(val model: ResolvedEvaluationModel) : EvaluationModelResolution

    data class Rejected(val failure: EvaluationFailure) : EvaluationModelResolution
}

class ControlledEvaluationModelResolver(
    private val supportedModels: SupportedEvaluationModelSource,
    private val modelStore: ModelStore,
) {
    fun resolve(identity: EvaluationModelIdentity): EvaluationModelResolution {
        val profile = supportedModels.find(identity.modelProfileId)
            ?: return rejected(EvaluationFailureCode.MODEL_UNSUPPORTED)

        if (profile.artifact.digest != identity.artifactDigest) {
            return rejected(EvaluationFailureCode.MODEL_UNSUPPORTED)
        }
        if (identity.quantization != null && profile.artifact.quantization != identity.quantization) {
            return rejected(EvaluationFailureCode.MODEL_UNSUPPORTED)
        }

        val stored = modelStore.find(identity.artifactDigest)
            ?: return rejected(EvaluationFailureCode.MODEL_NOT_INSTALLED)
        if (!stored.verified || stored.digest != identity.artifactDigest) {
            return rejected(EvaluationFailureCode.MODEL_NOT_INSTALLED)
        }

        return EvaluationModelResolution.Resolved(
            ResolvedEvaluationModel(
                identity = identity,
                profile = profile,
                storedModel = stored,
            ),
        )
    }

    private fun rejected(code: EvaluationFailureCode): EvaluationModelResolution.Rejected = EvaluationModelResolution.Rejected(
        EvaluationFailure(
            stage = EvaluationFailureStage.PREFLIGHT,
            code = code,
        ),
    )
}
