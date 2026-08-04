package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.store.ModelImportErrorCode
import io.github.daniele21.localllm.store.ModelImportException
import io.github.daniele21.localllm.store.ModelStore
import java.io.File

enum class ConsoleModelOperation {
    IMPORT,
    VERIFY,
    REMOVE,
}

data class ConsoleModelOperationOutcome(
    val operation: ConsoleModelOperation,
    val digest: ModelDigest?,
    val success: Boolean,
    val detail: String,
    val sourceError: String? = null,
)

data class ConsoleModelControlState(
    val available: Boolean,
    val source: String,
    val importAvailable: Boolean,
    val verifyAvailable: Boolean,
    val removeAvailable: Boolean,
    val executionInProgress: Boolean = false,
    val lastOperation: ConsoleModelOperationOutcome? = null,
    val sourceError: String? = null,
)

data class ConsoleModelImportRequest(
    val source: File,
    val fileName: String,
    val digest: ModelDigest,
    val sizeBytes: Long,
    val architecture: String,
    val quantization: String,
) {
    init {
        require(fileName.isNotBlank()) { "Model file name must not be blank" }
        require(fileName.lowercase().endsWith(".gguf")) { "Model file name must end with .gguf" }
        require(sizeBytes > 0) { "Model size must be positive" }
        require(architecture.isNotBlank()) { "Model architecture must not be blank" }
        require(quantization.isNotBlank()) { "Model quantization must not be blank" }
    }

    fun artifact(): GgufArtifact = GgufArtifact(
        digest = digest,
        fileName = fileName,
        sizeBytes = sizeBytes,
        architecture = architecture.trim(),
        quantization = quantization.trim(),
        source = ArtifactSource.Imported("console-storage-access-framework"),
    )
}

interface ConsoleModelControl {
    fun snapshot(): ConsoleModelControlState

    fun importModel(request: ConsoleModelImportRequest): ConsoleModelOperationOutcome

    fun verify(digest: ModelDigest): ConsoleModelOperationOutcome

    fun remove(digest: ModelDigest): ConsoleModelOperationOutcome
}

object DisconnectedModelControl : ConsoleModelControl {
    override fun snapshot(): ConsoleModelControlState = ConsoleModelControlState(
        available = false,
        source = "Not connected",
        importAvailable = false,
        verifyAvailable = false,
        removeAvailable = false,
    )

    override fun importModel(request: ConsoleModelImportRequest): ConsoleModelOperationOutcome = unavailable(
        ConsoleModelOperation.IMPORT,
        request.digest,
    )

    override fun verify(digest: ModelDigest): ConsoleModelOperationOutcome = unavailable(
        ConsoleModelOperation.VERIFY,
        digest,
    )

    override fun remove(digest: ModelDigest): ConsoleModelOperationOutcome = unavailable(
        ConsoleModelOperation.REMOVE,
        digest,
    )

    private fun unavailable(operation: ConsoleModelOperation, digest: ModelDigest): ConsoleModelOperationOutcome =
        ConsoleModelOperationOutcome(
            operation = operation,
            digest = digest,
            success = false,
            detail = MODEL_MANAGEMENT_ERROR,
            sourceError = MODEL_MANAGEMENT_ERROR,
        )
}

@Suppress("TooGenericExceptionCaught")
class ModelStoreConsoleModelControl(
    private val modelStore: ModelStore,
    private val source: String,
    private val loadedModelDigest: () -> ModelDigest? = { null },
) : ConsoleModelControl {
    override fun snapshot(): ConsoleModelControlState = ConsoleModelControlState(
        available = true,
        source = source,
        importAvailable = true,
        verifyAvailable = true,
        removeAvailable = true,
    )

    override fun importModel(request: ConsoleModelImportRequest): ConsoleModelOperationOutcome = try {
        val stored = modelStore.import(request.source, request.artifact())
        ConsoleModelOperationOutcome(
            operation = ConsoleModelOperation.IMPORT,
            digest = stored.digest,
            success = stored.verified,
            detail = if (stored.verified) "Model imported and verified" else "Model import verification failed",
        )
    } catch (error: ModelImportException) {
        ConsoleModelOperationOutcome(
            operation = ConsoleModelOperation.IMPORT,
            digest = request.digest,
            success = false,
            detail = error.code.safeImportDetail,
        )
    } catch (_: RuntimeException) {
        unavailable(ConsoleModelOperation.IMPORT, request.digest)
    }

    override fun verify(digest: ModelDigest): ConsoleModelOperationOutcome = try {
        val result = modelStore.verify(digest)
        ConsoleModelOperationOutcome(
            operation = ConsoleModelOperation.VERIFY,
            digest = digest,
            success = result.valid,
            detail = if (result.valid) "Model integrity verified" else "Model integrity check failed",
        )
    } catch (_: RuntimeException) {
        unavailable(ConsoleModelOperation.VERIFY, digest)
    }

    override fun remove(digest: ModelDigest): ConsoleModelOperationOutcome {
        if (loadedModelDigest() == digest) {
            return ConsoleModelOperationOutcome(
                operation = ConsoleModelOperation.REMOVE,
                digest = digest,
                success = false,
                detail = "Loaded model cannot be removed",
            )
        }
        return try {
            val removed = modelStore.remove(digest)
            ConsoleModelOperationOutcome(
                operation = ConsoleModelOperation.REMOVE,
                digest = digest,
                success = removed,
                detail = if (removed) "Model removed" else "Model not found",
            )
        } catch (_: RuntimeException) {
            unavailable(ConsoleModelOperation.REMOVE, digest)
        }
    }

    private fun unavailable(operation: ConsoleModelOperation, digest: ModelDigest): ConsoleModelOperationOutcome =
        ConsoleModelOperationOutcome(
            operation = operation,
            digest = digest,
            success = false,
            detail = MODEL_MANAGEMENT_ERROR,
            sourceError = MODEL_MANAGEMENT_ERROR,
        )
}

private val ModelImportErrorCode.safeImportDetail: String
    get() = when (this) {
        ModelImportErrorCode.INVALID_SOURCE -> "Selected model source is unavailable"
        ModelImportErrorCode.INVALID_DIGEST -> "Model digest is invalid"
        ModelImportErrorCode.SIZE_MISMATCH -> "Model size verification failed"
        ModelImportErrorCode.DIGEST_MISMATCH -> "Model digest verification failed"
        ModelImportErrorCode.DESTINATION_CONFLICT -> "Model destination contains conflicting content"
        ModelImportErrorCode.IO_FAILURE -> "Model import failed"
    }

private const val MODEL_MANAGEMENT_ERROR = "Model management unavailable"
