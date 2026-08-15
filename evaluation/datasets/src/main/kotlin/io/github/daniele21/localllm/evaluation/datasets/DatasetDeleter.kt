package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationResultRepository
import io.github.daniele21.localllm.evaluation.EvaluationRunQuery
import io.github.daniele21.localllm.evaluation.EvaluationRunState
import io.github.daniele21.localllm.evaluation.MAX_EVALUATION_HISTORY_LIMIT
import java.io.File

fun interface ActiveEvaluationDatasetUseProbe {
    suspend fun isActive(identity: EvaluationDatasetIdentity): Boolean
}

class EvaluationResultRepositoryDatasetUseProbe(private val repository: EvaluationResultRepository) : ActiveEvaluationDatasetUseProbe {
    override suspend fun isActive(identity: EvaluationDatasetIdentity): Boolean = repository.queryRuns(
        EvaluationRunQuery(
            states = ACTIVE_RUN_STATES,
            datasetId = identity.id,
            limit = MAX_EVALUATION_HISTORY_LIMIT,
        ),
    ).any { summary -> summary.config.dataset == identity }

    private companion object {
        val ACTIVE_RUN_STATES = setOf(
            EvaluationRunState.CREATED,
            EvaluationRunState.VALIDATING,
            EvaluationRunState.PREPARING_MODEL,
            EvaluationRunState.WARMING_UP,
            EvaluationRunState.RUNNING,
            EvaluationRunState.AGGREGATING,
            EvaluationRunState.CANCELLING,
        )
    }
}

enum class DatasetDeleteStatus {
    DELETED,
    NOT_FOUND,
    ACTIVE_RUN,
    IO_FAILURE,
}

class EvaluationDatasetDeleter(
    private val rootDirectory: File,
    private val registry: EvaluationDatasetRegistry,
    private val activeUseProbe: ActiveEvaluationDatasetUseProbe,
) {
    suspend fun delete(datasetId: EvaluationDatasetId, version: EvaluationDatasetVersion): DatasetDeleteStatus {
        val pack = registry.find(datasetId, version) ?: return DatasetDeleteStatus.NOT_FOUND
        if (!pack.directory.isOwnedBy(rootDirectory)) return DatasetDeleteStatus.IO_FAILURE
        if (activeUseProbe.isActive(pack.manifest.identity())) return DatasetDeleteStatus.ACTIVE_RUN
        if (!pack.directory.deleteRecursively()) return DatasetDeleteStatus.IO_FAILURE
        cleanupEmptyDatasetDirectory(pack.directory.parentFile)
        return DatasetDeleteStatus.DELETED
    }
}

private fun io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1.identity() = EvaluationDatasetIdentity(
    id = datasetId,
    version = version,
    digest = contentDigest,
)

private fun File.isOwnedBy(rootDirectory: File): Boolean = runCatching {
    val root = rootDirectory.canonicalFile.toPath()
    val candidate = canonicalFile.toPath()
    candidate != root && candidate.startsWith(root)
}.getOrDefault(false)

private fun cleanupEmptyDatasetDirectory(directory: File?) {
    if (directory == null || !directory.isDirectory) return
    if (directory.listFiles().orEmpty().isEmpty()) {
        directory.delete()
    }
}
