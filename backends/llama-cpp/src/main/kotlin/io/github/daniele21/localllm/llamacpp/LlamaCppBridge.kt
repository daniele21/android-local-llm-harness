package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.GgufModelProfile
import java.io.File

interface NativeLlamaApi {
    fun runtimeVersion(): String
    fun isLlamaCppLinked(): Boolean
    fun supportsMmap(): Boolean
    fun initialize(nativeLibraryDir: String): Array<String>
    fun backendDevices(): Array<String> = arrayOf("error", "NATIVE_PROTOCOL", "Backend device inventory is unavailable")
    fun shutdown(): Array<String>
    fun loadModel(path: String, nGpuLayers: Int, useMmap: Boolean, useMlock: Boolean): Array<String>
    fun unloadModel(modelHandle: Long): Array<String>
    fun inspectGguf(path: String): Array<String>
}

class JniLlamaApi : NativeLlamaApi {
    init {
        System.loadLibrary("local_llm_jni")
    }

    external override fun runtimeVersion(): String
    external override fun isLlamaCppLinked(): Boolean
    external override fun supportsMmap(): Boolean
    external override fun initialize(nativeLibraryDir: String): Array<String>
    external override fun backendDevices(): Array<String>
    external override fun shutdown(): Array<String>
    external override fun loadModel(path: String, nGpuLayers: Int, useMmap: Boolean, useMlock: Boolean): Array<String>
    external override fun unloadModel(modelHandle: Long): Array<String>
    external override fun inspectGguf(path: String): Array<String>
}

@Suppress("TooManyFunctions")
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
                "Pinned llama.cpp runtime linked"
            } else {
                "Native library loaded, but llama.cpp symbols are unavailable"
            },
        )
    }

    fun initializeRuntime(nativeLibraryDir: File): RuntimeInitializationResult {
        if (!nativeLibraryDir.isDirectory) {
            return RuntimeInitializationResult.Failure(
                NativeRuntimeError(
                    code = NativeRuntimeErrorCode.INVALID_ARGUMENT,
                    message = "Native library directory does not exist: ${nativeLibraryDir.path}",
                ),
            )
        }

        val initialization = decodeInitialization(nativeApi.initialize(nativeLibraryDir.absolutePath))
        if (initialization !is RuntimeInitializationResult.Success) {
            return initialization
        }

        return when (val inventory = decodeBackendDeviceInventory(nativeApi.backendDevices())) {
            is BackendDeviceInventoryResult.Success -> initialization.copy(
                registeredDeviceCount = inventory.devices.size,
                devices = inventory.devices,
            )

            is BackendDeviceInventoryResult.Unavailable -> initialization.copy(
                deviceInventoryError = inventory.error,
            )
        }
    }

    fun shutdownRuntime(): NativeOperationResult = decodeOperation(nativeApi.shutdown())

    fun loadModel(file: File, profile: GgufModelProfile): ModelLoadResult {
        if (!file.isFile) {
            return ModelLoadResult.Failure(
                NativeRuntimeError(
                    code = NativeRuntimeErrorCode.FILE_NOT_FOUND,
                    message = "GGUF model does not exist: ${file.path}",
                ),
            )
        }

        return decodeModelLoad(
            response = nativeApi.loadModel(
                path = file.absolutePath,
                nGpuLayers = profile.gpuLayers,
                useMmap = profile.useMmap,
                useMlock = profile.useMlock,
            ),
            file = file,
            profile = profile,
        )
    }

    fun unloadModel(model: LoadedNativeModel): NativeOperationResult = decodeOperation(
        nativeApi.unloadModel(model.handle.value),
    )

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

    private fun decodeInitialization(response: Array<String>): RuntimeInitializationResult {
        if (response.size == INITIALIZATION_FIELD_COUNT && response[0] == OK) {
            return try {
                RuntimeInitializationResult.Success(deviceCapacity = response[1].toInt())
            } catch (error: NumberFormatException) {
                RuntimeInitializationResult.Failure(protocolError("Native device capacity is invalid: ${error.message}"))
            }
        }
        return RuntimeInitializationResult.Failure(decodeStandardError(response))
    }

    private fun decodeBackendDeviceInventory(response: Array<String>): BackendDeviceInventoryResult {
        if (response.size == ERROR_FIELD_COUNT && response[0] == ERROR) {
            return BackendDeviceInventoryResult.Unavailable(decodeStandardError(response))
        }
        if (response.size < BACKEND_DEVICE_HEADER_FIELD_COUNT || response[0] != OK) {
            return BackendDeviceInventoryResult.Unavailable(protocolError("Malformed native backend device inventory"))
        }

        return try {
            val deviceCount = response[1].toInt()
            if (deviceCount < 0 || response.size != BACKEND_DEVICE_HEADER_FIELD_COUNT + deviceCount * BACKEND_DEVICE_FIELD_COUNT) {
                return BackendDeviceInventoryResult.Unavailable(
                    protocolError("Native backend device inventory field count does not match declared device count"),
                )
            }

            val devices = buildList(deviceCount) {
                repeat(deviceCount) { ordinal ->
                    val offset = BACKEND_DEVICE_HEADER_FIELD_COUNT + ordinal * BACKEND_DEVICE_FIELD_COUNT
                    val index = response[offset].toInt()
                    if (index != ordinal) {
                        throw IllegalArgumentException("Backend device index $index is out of canonical order at position $ordinal")
                    }
                    add(
                        NativeBackendDevice(
                            index = index,
                            name = response[offset + 1],
                            description = response[offset + 2],
                            type = NativeBackendDeviceType.entries.firstOrNull { it.name == response[offset + 3] }
                                ?: NativeBackendDeviceType.UNKNOWN,
                            deviceId = response[offset + 4].ifBlank { null },
                            memoryFreeBytes = response[offset + 5].toULong(),
                            memoryTotalBytes = response[offset + 6].toULong(),
                            capabilities = NativeBackendDeviceCapabilities(
                                asynchronous = response[offset + 7].toBooleanStrict(),
                                hostBuffer = response[offset + 8].toBooleanStrict(),
                                bufferFromHostPointer = response[offset + 9].toBooleanStrict(),
                                events = response[offset + 10].toBooleanStrict(),
                            ),
                        ),
                    )
                }
            }
            BackendDeviceInventoryResult.Success(devices)
        } catch (error: IllegalArgumentException) {
            BackendDeviceInventoryResult.Unavailable(
                protocolError("Native backend device inventory is invalid: ${error.message}"),
            )
        }
    }

    private fun decodeModelLoad(response: Array<String>, file: File, profile: GgufModelProfile): ModelLoadResult {
        if (response.size == MODEL_LOAD_FIELD_COUNT && response[0] == OK) {
            return try {
                ModelLoadResult.Success(
                    LoadedNativeModel(
                        handle = NativeModelHandle(response[1].toLong()),
                        profileId = profile.id,
                        digest = profile.artifact.digest,
                        file = file,
                        loadDurationMs = response[2].toLong(),
                        requestedExecution = NativeModelExecutionRequest(
                            gpuLayers = profile.gpuLayers,
                            useMmap = profile.useMmap,
                            useMlock = profile.useMlock,
                        ),
                    ),
                )
            } catch (error: NumberFormatException) {
                ModelLoadResult.Failure(protocolError("Native model load response is invalid: ${error.message}"))
            } catch (error: IllegalArgumentException) {
                ModelLoadResult.Failure(protocolError("Native model handle is invalid: ${error.message}"))
            }
        }
        return ModelLoadResult.Failure(decodeStandardError(response))
    }

    private fun decodeOperation(response: Array<String>): NativeOperationResult {
        if (response.size == OPERATION_FIELD_COUNT && response[0] == OK) {
            return NativeOperationResult.Success
        }
        return NativeOperationResult.Failure(decodeStandardError(response))
    }

    private fun decodeInspection(response: Array<String>): GgufInspectionResult {
        if (response.isEmpty()) {
            return inspectionProtocolFailure("Native GGUF inspection returned an empty response")
        }

        return when (response[0]) {
            OK -> decodeInspectionSuccess(response)
            ERROR -> decodeInspectionFailure(response)
            else -> inspectionProtocolFailure("Unknown native GGUF inspection status: ${response[0]}")
        }
    }

    private fun decodeInspectionSuccess(response: Array<String>): GgufInspectionResult {
        if (response.size != INSPECTION_SUCCESS_FIELD_COUNT) {
            return inspectionProtocolFailure(
                "Expected $INSPECTION_SUCCESS_FIELD_COUNT success fields, received ${response.size}",
            )
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
                    contextLength = response[9].ifBlank { null }?.toLong(),
                    blockCount = response[10].ifBlank { null }?.toLong(),
                    embeddingLength = response[11].ifBlank { null }?.toLong(),
                ),
            )
        } catch (error: NumberFormatException) {
            inspectionProtocolFailure("Native GGUF inspection returned invalid metadata: ${error.message}")
        }
    }

    private fun decodeInspectionFailure(response: Array<String>): GgufInspectionResult {
        if (response.size != ERROR_FIELD_COUNT) {
            return inspectionProtocolFailure("Expected $ERROR_FIELD_COUNT failure fields, received ${response.size}")
        }

        val code = GgufInspectionErrorCode.entries.firstOrNull { it.name == response[1] }
            ?: GgufInspectionErrorCode.NATIVE_PROTOCOL
        return GgufInspectionResult.Failure(GgufInspectionError(code = code, message = response[2]))
    }

    private fun decodeStandardError(response: Array<String>): NativeRuntimeError {
        if (response.size != ERROR_FIELD_COUNT || response[0] != ERROR) {
            return protocolError("Malformed native response: ${response.joinToString(separator = "|")}")
        }
        val code = NativeRuntimeErrorCode.entries.firstOrNull { it.name == response[1] }
            ?: NativeRuntimeErrorCode.NATIVE_PROTOCOL
        return NativeRuntimeError(code = code, message = response[2])
    }

    private fun protocolError(message: String): NativeRuntimeError = NativeRuntimeError(
        code = NativeRuntimeErrorCode.NATIVE_PROTOCOL,
        message = message,
    )

    private fun inspectionProtocolFailure(message: String): GgufInspectionResult.Failure = GgufInspectionResult.Failure(
        GgufInspectionError(
            code = GgufInspectionErrorCode.NATIVE_PROTOCOL,
            message = message,
        ),
    )

    private companion object {
        const val OK = "ok"
        const val ERROR = "error"
        const val INITIALIZATION_FIELD_COUNT = 2
        const val MODEL_LOAD_FIELD_COUNT = 3
        const val OPERATION_FIELD_COUNT = 1
        const val ERROR_FIELD_COUNT = 3
        const val INSPECTION_SUCCESS_FIELD_COUNT = 12
        const val BACKEND_DEVICE_HEADER_FIELD_COUNT = 2
        const val BACKEND_DEVICE_FIELD_COUNT = 11
    }
}

@JvmInline
value class NativeModelHandle(val value: Long) {
    init {
        require(value > 0) { "Native model handle must be positive" }
    }
}

data class NativeModelExecutionRequest(
    val gpuLayers: Int,
    val useMmap: Boolean,
    val useMlock: Boolean,
)

data class LoadedNativeModel(
    val handle: NativeModelHandle,
    val profileId: String,
    val digest: ModelDigest,
    val file: File,
    val loadDurationMs: Long,
    val requestedExecution: NativeModelExecutionRequest = NativeModelExecutionRequest(
        gpuLayers = 0,
        useMmap = true,
        useMlock = false,
    ),
)

data class NativeBackendDeviceCapabilities(
    val asynchronous: Boolean,
    val hostBuffer: Boolean,
    val bufferFromHostPointer: Boolean,
    val events: Boolean,
)

enum class NativeBackendDeviceType {
    CPU,
    GPU,
    IGPU,
    ACCELERATOR,
    META,
    UNKNOWN,
}

data class NativeBackendDevice(
    val index: Int,
    val name: String,
    val description: String,
    val type: NativeBackendDeviceType,
    val deviceId: String?,
    val memoryFreeBytes: ULong,
    val memoryTotalBytes: ULong,
    val capabilities: NativeBackendDeviceCapabilities,
)

private sealed interface BackendDeviceInventoryResult {
    data class Success(val devices: List<NativeBackendDevice>) : BackendDeviceInventoryResult
    data class Unavailable(val error: NativeRuntimeError) : BackendDeviceInventoryResult
}

sealed interface RuntimeInitializationResult {
    data class Success(
        val deviceCapacity: Int,
        val registeredDeviceCount: Int? = null,
        val devices: List<NativeBackendDevice>? = null,
        val deviceInventoryError: NativeRuntimeError? = null,
    ) : RuntimeInitializationResult

    data class Failure(val error: NativeRuntimeError) : RuntimeInitializationResult
}

sealed interface ModelLoadResult {
    data class Success(val model: LoadedNativeModel) : ModelLoadResult
    data class Failure(val error: NativeRuntimeError) : ModelLoadResult
}

sealed interface NativeOperationResult {
    data object Success : NativeOperationResult
    data class Failure(val error: NativeRuntimeError) : NativeOperationResult
}

data class NativeRuntimeError(val code: NativeRuntimeErrorCode, val message: String)

enum class NativeRuntimeErrorCode {
    INVALID_ARGUMENT,
    FILE_NOT_FOUND,
    NOT_INITIALIZED,
    BACKEND_UNAVAILABLE,
    MODEL_LOAD_FAILED,
    MODEL_IN_USE,
    UNKNOWN_HANDLE,
    BUSY,
    INTERNAL,
    NATIVE_PROTOCOL,
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
    val contextLength: Long?,
    val blockCount: Long?,
    val embeddingLength: Long?,
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
