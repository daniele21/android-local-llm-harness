package io.github.daniele21.localllm.store

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.GgufArtifact
import java.io.File

interface ModelStore {
    fun find(digest: ModelDigest): StoredModel?
    fun import(source: File, artifact: GgufArtifact): StoredModel
    fun verify(digest: ModelDigest): VerificationResult
    fun remove(digest: ModelDigest): Boolean
    fun snapshot(): ModelStoreSnapshot
}

data class StoredModel(val digest: ModelDigest, val file: File, val sizeBytes: Long, val verified: Boolean)

data class VerificationResult(val valid: Boolean, val actualDigest: ModelDigest?, val detail: String)

data class ModelStoreSnapshot(val modelCount: Int, val totalBytes: Long, val entries: List<StoredModel>)

class ModelImportException(
    val code: ModelImportErrorCode,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

enum class ModelImportErrorCode {
    INVALID_SOURCE,
    INVALID_DIGEST,
    SIZE_MISMATCH,
    DIGEST_MISMATCH,
    DESTINATION_CONFLICT,
    IO_FAILURE,
}

object ModelStoreLayout {
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun canonicalDigest(digest: ModelDigest): ModelDigest {
        require(sha256Pattern.matches(digest.sha256)) {
            "SHA-256 digest must contain exactly 64 hexadecimal characters"
        }
        return ModelDigest(digest.sha256.lowercase())
    }

    fun relativeArtifactPath(digest: ModelDigest): String {
        val canonical = canonicalDigest(digest).sha256
        return "models/sha256/${canonical.take(2)}/$canonical/model.gguf"
    }
}
