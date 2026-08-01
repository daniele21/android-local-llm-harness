package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.llamacpp.ContextCreationResult
import io.github.daniele21.localllm.llamacpp.GenerationNativeOperationResult
import io.github.daniele21.localllm.llamacpp.LlamaCppBridge
import io.github.daniele21.localllm.llamacpp.LlamaCppGenerationBridge
import io.github.daniele21.localllm.llamacpp.LlamaCppStreamingBridge
import io.github.daniele21.localllm.llamacpp.LoadedNativeContext
import io.github.daniele21.localllm.llamacpp.LoadedNativeModel
import io.github.daniele21.localllm.llamacpp.ModelLoadResult
import io.github.daniele21.localllm.llamacpp.NativeGenerationConfig
import io.github.daniele21.localllm.llamacpp.NativeOperationResult
import io.github.daniele21.localllm.llamacpp.NativeStreamingListener
import io.github.daniele21.localllm.llamacpp.NativeStreamingResult
import io.github.daniele21.localllm.llamacpp.RuntimeInitializationResult
import io.github.daniele21.localllm.llamacpp.StreamingCancelResult
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.store.StoredModel
import java.io.File

class LlamaCppInferenceBackend(
    private val nativeLibraryDir: File,
    private val lifecycleBridge: LlamaCppBridge = LlamaCppBridge(),
    private val generationBridge: LlamaCppGenerationBridge = LlamaCppGenerationBridge(),
    private val streamingBridge: LlamaCppStreamingBridge = LlamaCppStreamingBridge(),
) : InferenceBackend {
    override val id: String = "llama.cpp"

    override fun initialize() {
        when (val result = lifecycleBridge.initializeRuntime(nativeLibraryDir)) {
            is RuntimeInitializationResult.Success -> Unit
            is RuntimeInitializationResult.Failure -> throw result.error.asBackendException()
        }
    }

    override fun shutdown() {
        when (val result = lifecycleBridge.shutdownRuntime()) {
            NativeOperationResult.Success -> Unit
            is NativeOperationResult.Failure -> throw result.error.asBackendException()
        }
    }

    override fun loadModel(
        storedModel: StoredModel,
        profile: GgufModelProfile,
    ): BackendModelHandle {
        require(storedModel.digest == profile.artifact.digest) {
            "Stored model digest does not match profile ${profile.id}"
        }
        return when (val result = lifecycleBridge.loadModel(storedModel.file, profile)) {
            is ModelLoadResult.Success -> LlamaBackendModel(result.model)
            is ModelLoadResult.Failure -> throw result.error.asBackendException()
        }
    }

    override fun unloadModel(model: BackendModelHandle) {
        val nativeModel = model.requireLlamaModel()
        when (val result = lifecycleBridge.unloadModel(nativeModel.delegate)) {
            NativeOperationResult.Success -> Unit
            is NativeOperationResult.Failure -> throw result.error.asBackendException()
        }
    }

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
    ): BackendContextHandle {
        val nativeModel = model.requireLlamaModel()
        return when (val result = generationBridge.createContext(nativeModel.delegate, profile)) {
            is ContextCreationResult.Success -> LlamaBackendContext(nativeModel, result.context)
            is ContextCreationResult.Failure -> throw result.error.asBackendException()
        }
    }

    override fun releaseContext(context: BackendContextHandle) {
        val nativeContext = context.requireLlamaContext()
        when (val result = generationBridge.releaseContext(nativeContext.delegate)) {
            GenerationNativeOperationResult.Success -> Unit
            is GenerationNativeOperationResult.Failure -> throw result.error.asBackendException()
        }
    }

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome {
        val nativeContext = context.requireLlamaContext()
        val config = NativeGenerationConfig(
            maxOutputTokens = request.maxOutputTokens,
            temperature = request.temperature,
            topP = request.topP,
            topK = request.topK,
            seed = request.seed,
        )
        return when (
            val result = streamingBridge.generate(
                context = nativeContext.delegate,
                requestId = request.requestId,
                prompt = request.prompt,
                config = config,
                listener = NativeStreamingListener { chunk ->
                    onChunk(chunk.text, chunk.generatedTokens)
                },
            )
        ) {
            is NativeStreamingResult.Completed -> BackendGenerationOutcome.Completed(result.metrics.toBackendMetrics())
            is NativeStreamingResult.Cancelled -> BackendGenerationOutcome.Cancelled(result.metrics.toBackendMetrics())
            is NativeStreamingResult.Failure -> throw result.error.asBackendException()
        }
    }

    override fun cancel(requestId: String): Boolean = when (val result = streamingBridge.cancel(requestId)) {
        is StreamingCancelResult.Accepted -> result.wasRunning
        is StreamingCancelResult.Failure -> throw result.error.asBackendException()
    }

    private data class LlamaBackendModel(
        val delegate: LoadedNativeModel,
    ) : BackendModelHandle {
        override val digest = delegate.digest
        override val profileId = delegate.profileId
        override val loadDurationMs = delegate.loadDurationMs
    }

    private data class LlamaBackendContext(
        override val model: LlamaBackendModel,
        val delegate: LoadedNativeContext,
    ) : BackendContextHandle

    private fun BackendModelHandle.requireLlamaModel(): LlamaBackendModel = this as? LlamaBackendModel
        ?: throw BackendException("BACKEND_MISMATCH", "Model handle was not created by the llama.cpp backend")

    private fun BackendContextHandle.requireLlamaContext(): LlamaBackendContext = this as? LlamaBackendContext
        ?: throw BackendException("BACKEND_MISMATCH", "Context handle was not created by the llama.cpp backend")
}

private fun io.github.daniele21.localllm.llamacpp.NativeStreamingMetrics.toBackendMetrics(): BackendGenerationMetrics =
    BackendGenerationMetrics(
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        promptDurationMs = promptDurationMs,
        generationDurationMs = generationDurationMs,
    )

private fun io.github.daniele21.localllm.llamacpp.NativeRuntimeError.asBackendException(): BackendException =
    BackendException(code.name, message)

private fun io.github.daniele21.localllm.llamacpp.GenerationNativeError.asBackendException(): BackendException =
    BackendException(code.name, message)

private fun io.github.daniele21.localllm.llamacpp.StreamingNativeError.asBackendException(): BackendException =
    BackendException(code.name, message)
