package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.install.GgufArtifactInspectionFailureCode
import io.github.daniele21.localllm.install.GgufArtifactInspectionResult
import io.github.daniele21.localllm.install.GgufArtifactInspector
import io.github.daniele21.localllm.install.GgufArtifactMetadata
import java.io.File

class LlamaCppGgufArtifactInspector(private val bridge: LlamaCppBridge = LlamaCppBridge()) : GgufArtifactInspector {
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
