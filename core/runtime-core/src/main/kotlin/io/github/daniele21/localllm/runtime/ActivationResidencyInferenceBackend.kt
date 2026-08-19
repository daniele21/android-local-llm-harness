package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.models.GgufModelProfile

/**
 * Shared-runtime backend decorator that makes activation leases authoritative at the physical
 * model-release boundary. It deliberately delegates every operation except model unload.
 *
 * RuntimeOrchestrator already restores RESIDENT state when backend unload fails, so rejecting the
 * physical release here prevents an ordinary idle/switch path from evicting a model protected by
 * an active activation without duplicating model-handle ownership.
 */
class ActivationResidencyInferenceBackend(
    private val delegate: InferenceBackend,
    private val activationResidency: ActivationResidencyCoordinator,
) : InferenceBackend {
    override val id: String
        get() = delegate.id

    override val revision: String?
        get() = delegate.revision

    override fun initialize() = delegate.initialize()

    override fun shutdown() = delegate.shutdown()

    override fun loadModel(source: BackendModelSource, profile: GgufModelProfile): BackendModelHandle = delegate.loadModel(source, profile)

    override fun unloadModel(model: BackendModelHandle) {
        if (activationResidency.protects(model.digest)) {
            throw BackendException(
                code = MODEL_PROTECTED_CODE,
                message = "Model ${model.digest.sha256} is protected by an active use-case activation",
            )
        }
        delegate.unloadModel(model)
    }

    override fun modelCapabilities(model: BackendModelHandle): BackendModelCapabilities = delegate.modelCapabilities(model)

    override fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest): BackendPromptPlan =
        delegate.planPrompt(model, request)

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle = delegate.createContext(model, profile, configuration)

    override fun releaseContext(context: BackendContextHandle) = delegate.releaseContext(context)

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome = delegate.generate(context, request, onChunk)

    override fun cancel(requestId: String): Boolean = delegate.cancel(requestId)

    companion object {
        const val MODEL_PROTECTED_CODE = "MODEL_PROTECTED_BY_ACTIVATION"
    }
}
