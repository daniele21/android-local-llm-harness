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

object ModelStoreLayout {
    fun relativeArtifactPath(digest: ModelDigest): String {
        require(digest.sha256.length >= 2) { "SHA-256 digest is too short" }
        return "models/sha256/${digest.sha256.take(2)}/${digest.sha256}/model.gguf"
    }
}
