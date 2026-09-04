package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.GgufModelProfile

internal data class FakeBackendChunk(val text: String, val generatedTokens: Int)

internal data class FakeBackendFailure(val code: String, val message: String)

internal class DeterministicFakeInferenceBackend(
    override val id: String = "deterministic-fake",
    override val revision: String? = "test-v1",
) : InferenceBackend {
    var initializeCalls = 0
        private set
    var shutdownCalls = 0
        private set
    var loadCalls = 0
        private set
    var unloadCalls = 0
        private set
    var createContextCalls = 0
        private set
    var releaseContextCalls = 0
        private set
    var generateCalls = 0
        private set
    var cancelCalls = 0
        private set

    var initializeFailure: FakeBackendFailure? = null
    var loadFailure: FakeBackendFailure? = null
    var unloadFailure: FakeBackendFailure? = null
    var promptPlanningFailure: FakeBackendFailure? = null
    var contextCreationFailure: FakeBackendFailure? = null
    var contextReleaseFailure: FakeBackendFailure? = null
    var generationFailure: FakeBackendFailure? = null
    var generationFailureAfterChunks: Int? = null
    var cancellationFailure: FakeBackendFailure? = null
    var cancellationAccepted: Boolean = true
    var generationOutcome: BackendGenerationOutcome =
        BackendGenerationOutcome.Completed(
            BackendGenerationMetrics(
                inputTokens = 4,
                outputTokens = 2,
                promptDurationMs = 2,
                generationDurationMs = 4,
            ),
        )
    var chunks: List<FakeBackendChunk> =
        listOf(
            FakeBackendChunk("deterministic ", 1),
            FakeBackendChunk("response", 2),
        )

    override fun initialize() {
        initializeCalls += 1
        initializeFailure?.throwBackendException()
    }

    override fun shutdown() {
        shutdownCalls += 1
    }

    override fun loadModel(source: BackendModelSource, profile: GgufModelProfile): BackendModelHandle {
        loadCalls += 1
        loadFailure?.throwBackendException()
        require(source.digest == profile.artifact.digest) { "Fake backend model/profile digest mismatch" }
        return FakeModelHandle(source.digest, profile.id)
    }

    override fun unloadModel(model: BackendModelHandle) {
        require(model is FakeModelHandle) { "Model handle was not created by deterministic fake" }
        unloadCalls += 1
        unloadFailure?.throwBackendException()
    }

    override fun modelCapabilities(model: BackendModelHandle): BackendModelCapabilities {
        require(model is FakeModelHandle) { "Model handle was not created by deterministic fake" }
        return BackendModelCapabilities(
            maximumContextTokens = 4_096,
            supportsGrammar = true,
            supportsReasoningTransition = true,
        )
    }

    override fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest): BackendPromptPlan {
        require(model is FakeModelHandle) { "Model handle was not created by deterministic fake" }
        promptPlanningFailure?.throwBackendException()
        return fakePromptPlan(request)
    }

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle {
        require(model is FakeModelHandle) { "Model handle was not created by deterministic fake" }
        require(model.profileId == profile.id) { "Fake backend context profile mismatch" }
        createContextCalls += 1
        contextCreationFailure?.throwBackendException()
        return FakeContextHandle(model, configuration.contextSize)
    }

    override fun releaseContext(context: BackendContextHandle) {
        require(context is FakeContextHandle) { "Context handle was not created by deterministic fake" }
        releaseContextCalls += 1
        contextReleaseFailure?.throwBackendException()
    }

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome {
        require(context is FakeContextHandle) { "Context handle was not created by deterministic fake" }
        generateCalls += 1
        if (generationFailureAfterChunks == null) {
            generationFailure?.throwBackendException()
        }
        for ((index, chunk) in chunks.withIndex()) {
            if (!onChunk(chunk.text, chunk.generatedTokens)) {
                return BackendGenerationOutcome.Cancelled(
                    BackendGenerationMetrics(
                        inputTokens = 4,
                        outputTokens = (chunk.generatedTokens - 1).coerceAtLeast(0),
                        promptDurationMs = 2,
                        generationDurationMs = 1,
                    ),
                )
            }
            if (generationFailureAfterChunks == index + 1) {
                generationFailure?.throwBackendException()
            }
        }
        return generationOutcome
    }

    override fun cancel(requestId: String): Boolean {
        require(requestId.isNotBlank()) { "Request ID must not be blank" }
        cancelCalls += 1
        cancellationFailure?.throwBackendException()
        return cancellationAccepted
    }

    private fun FakeBackendFailure.throwBackendException(): Nothing = throw BackendException(code, message)

    private data class FakeModelHandle(
        override val digest: ModelDigest,
        override val profileId: String,
        override val loadDurationMs: Long = 1,
    ) : BackendModelHandle

    private data class FakeContextHandle(override val model: BackendModelHandle, override val contextSize: Int) : BackendContextHandle
}
