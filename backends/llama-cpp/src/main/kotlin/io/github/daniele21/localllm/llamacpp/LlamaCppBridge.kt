package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.models.GgufModelProfile
import java.io.File

interface NativeLlamaApi {
    fun runtimeVersion(): String
    fun isLlamaCppLinked(): Boolean
    fun supportsMmap(): Boolean
    fun inspectGguf(path: String): Array<String>
}

class JniLlamaApi : NativeLlamaApi {
    init {
        System.loadLibrary("local_llm_jni")
    }

    external override fun runtimeVersion(): String
    external override fun isLlamaCppLinked(): Boolean
    external override fun supportsMmap(): Boolean
    external override fun inspectGguf(path: String): Array<String>
}

class LlamaCppBridge(private val nativeApi: NativeLlamaApi = JniLlamaApi()) {
    fun inspect(profile: GgufModelProfile): NativeRuntimeStatus {
        val linked = nativeApi.isLlamaCppLinked()
        val supportsMmap = nativeApi.supportsMmap()
        return NativeRuntimeStatus(
            linked = linked,
            runtimeVersion = nativeApi.runtimeVersion(),
            modelProfileId = profile.id,
            supportsMmap = supportsMmap,
            detail = if (linked) {
                "Pinned llama.cpp CPU backend linked"
            } else {
                "Native library loaded, but llama.cpp did not expose a runtime device"
            },
        )
    }

    fun inspectGguf(file: File): GgufInspectionResult {
        if (!file.exists()) {
            return GgufInspectionResult.Failure(
                GgufInspectionError(
                    code = GgufInspectionErrorCode.FILE_NOT_FOUND,
                    message = "GGUF file does not exist: ${file.path}",
                ),
            )
        }
        if (!file.isFile) {
            return GgufInspectionResult.Failure(
                GgufInspectionError(
                    code = GgufInspectionErrorCode.INVALID_ARGUMENT,
                    message = "GGUF path is not a regular file: ${file.path}",
                ),
            )
        }

        return decodeInspection(nativeApi.inspectGguf(file.absolutePath))
    }

    private fun decodeInspection(response: Array<String>): GgufInspectionResult {
        if (response.isEmpty()) {
            return protocolFailure("Native GGUF inspection returned an empty response")
        }

        return when (response[0]) {
            "ok" -> decodeSuccess(response)
            "error" -> decodeFailure(response)
            else -> protocolFailure("Unknown native GGUF inspection status: ${response[0]}")
        }
    }

    private fun decodeSuccess(response: Array<String>): GgufInspectionResult {
        if (response.size != SUCCESS_FIELD_COUNT) {
            return protocolFailure("Expected $SUCCESS_FIELD_COUNT success fields, received ${response.size}")
        }

        return try {
            GgufInspectionResult.Success(
                GgufMetadata(
                    version = response[1].toUInt(),
                    alignment = response[2].toULong(),
                    dataOffset = response[3].toULong(),
                    keyValueCount = response[4].toLong(),
                    tensorCount = response[5].toLong(),
                    architecture = response[6].ifBlank { null },
                    name = response[7].ifBlank { null },
                    fileType = response[8].ifBlank { null }?.toLong(),
                ),
            )
        } catch (error: NumberFormatException) {
            protocolFailure("Native GGUF inspection returned a non-numeric metadata field: ${error.message}")
        }
    }

    private fun decodeFailure(response: Array<String>): GgufInspectionResult {
        if (response.size != FAILURE_FIELD_COUNT) {
            return protocolFailure("Expected $FAILURE_FIELD_COUNT failure fields, received ${response.size}")
        }

        val code = GgufInspectionErrorCode.entries.firstOrNull { it.name == response[1] }
            ?: GgufInspectionErrorCode.NATIVE_PROTOCOL
        return GgufInspectionResult.Failure(GgufInspectionError(code = code, message = response[2]))
    }

    private fun protocolFailure(message: String): GgufInspectionResult.Failure =
        GgufInspectionResult.Failure(
            GgufInspectionError(
                code = GgufInspectionErrorCode.NATIVE_PROTOCOL,
                message = message,
            ),
        )

    private companion object {
        const val SUCCESS_FIELD_COUNT = 9
        const val FAILURE_FIELD_COUNT = 3
    }
}

data class NativeRuntimeStatus(
    val linked: Boolean,
    val runtimeVersion: String,
    val modelProfileId: String,
    val supportsMmap: Boolean,
    val detail: String,
)

data class GgufMetadata(
    val version: UInt,
    val alignment: ULong,
    val dataOffset: ULong,
    val keyValueCount: Long,
    val tensorCount: Long,
    val architecture: String?,
    val name: String?,
    val fileType: Long?,
)

sealed interface GgufInspectionResult {
    data class Success(val metadata: GgufMetadata) : GgufInspectionResult
    data class Failure(val error: GgufInspectionError) : GgufInspectionResult
}

data class GgufInspectionError(val code: GgufInspectionErrorCode, val message: String)

enum class GgufInspectionErrorCode {
    INVALID_ARGUMENT,
    FILE_NOT_FOUND,
    INVALID_MAGIC,
    PARSE_FAILED,
    NATIVE_PROTOCOL,
}
