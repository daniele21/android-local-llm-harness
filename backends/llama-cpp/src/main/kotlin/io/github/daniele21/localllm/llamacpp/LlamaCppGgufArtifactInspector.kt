package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.install.GgufArtifactInspectionFailureCode
import io.github.daniele21.localllm.install.GgufArtifactInspectionResult
import io.github.daniele21.localllm.install.GgufArtifactInspector
import io.github.daniele21.localllm.install.GgufArtifactMetadata
import java.io.File

/**
 * GGUF metadata adapter that keeps JNI completely out of composition/startup.
 *
 * The default bridge owns [JniLlamaApi], whose construction loads local_llm_jni. Model distribution is created
 * during the phone app cold start, while GGUF inspection is only needed when an artifact is actually installed.
 * Keep the bridge lazy so opening or navigating the Harness never depends on loading the native runtime.
 */
class LlamaCppGgufArtifactInspector internal constructor(private val bridgeProvider: () -> LlamaCppBridge) : GgufArtifactInspector {
    constructor() : this({ LlamaCppBridge() })

    constructor(bridge: LlamaCppBridge) : this({ bridge })

    private val bridge: LlamaCppBridge by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { bridgeProvider() }

    override fun inspect(file: File): GgufArtifactInspectionResult = when (val result = bridge.inspectGguf(file)) {
        is GgufInspectionResult.Success ->
            GgufArtifactInspectionResult.Success(
                GgufArtifactMetadata(
                    version = result.metadata.version,
                    architecture = result.metadata.architecture,
                    name = result.metadata.name,
                    fileType = result.metadata.fileType,
                    keyValueCount = result.metadata.keyValueCount,
                    tensorCount = result.metadata.tensorCount,
                    contextLength = result.metadata.contextLength,
                    blockCount = result.metadata.blockCount,
                    embeddingLength = result.metadata.embeddingLength,
                ),
            )

        is GgufInspectionResult.Failure ->
            GgufArtifactInspectionResult.Failure(
                when (result.error.code) {
                    GgufInspectionErrorCode.FILE_NOT_FOUND ->
                        GgufArtifactInspectionFailureCode.FILE_NOT_FOUND

                    GgufInspectionErrorCode.INVALID_MAGIC,
                    GgufInspectionErrorCode.PARSE_FAILED,
                    -> GgufArtifactInspectionFailureCode.INVALID_GGUF

                    GgufInspectionErrorCode.INVALID_ARGUMENT,
                    GgufInspectionErrorCode.NATIVE_PROTOCOL,
                    -> GgufArtifactInspectionFailureCode.INSPECTION_FAILED
                },
            )
    }
}
