package io.github.daniele21.localllm.evaluation.datasetadapter

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.SamplingSelection
import io.github.daniele21.localllm.evaluation.datasets.DatasetDigestVerification
import io.github.daniele21.localllm.evaluation.datasets.DatasetParseErrorCode
import io.github.daniele21.localllm.evaluation.datasets.EvaluationDatasetContentDigester
import io.github.daniele21.localllm.evaluation.datasets.EvaluationDatasetJsonlParser
import io.github.daniele21.localllm.evaluation.datasets.EvaluationDatasetParseException
import io.github.daniele21.localllm.evaluation.datasets.EvaluationDatasetRegistry
import io.github.daniele21.localllm.evaluation.engine.EvaluationCaseDefinitionSource
import io.github.daniele21.localllm.evaluation.engine.EvaluationDatasetPreflight
import java.io.File

class InstalledEvaluationDatasetAdapter(
    rootDirectory: File,
    private val parser: EvaluationDatasetJsonlParser = EvaluationDatasetJsonlParser(),
) : EvaluationDatasetPreflight,
    EvaluationCaseDefinitionSource {
    private val registry = EvaluationDatasetRegistry(rootDirectory)
    private val cacheLock = Any()

    @Volatile
    private var cached: DatasetSnapshot? = null

    override fun validate(dataset: EvaluationDatasetIdentity, sampling: SamplingSelection): EvaluationFailure? {
        if (sampling.dataset != dataset) return preflightFailure(EvaluationFailureCode.SAMPLE_SET_INVALID)
        return when (val load = loadSnapshot(dataset)) {
            is SnapshotLoad.Failed -> preflightFailure(load.code)
            is SnapshotLoad.Loaded -> {
                val knownCaseIds = load.snapshot.byId.keys
                if (sampling.orderedCaseIds.all { it in knownCaseIds }) {
                    null
                } else {
                    preflightFailure(EvaluationFailureCode.SAMPLE_SET_INVALID)
                }
            }
        }
    }

    override fun load(config: EvaluationRunConfig, caseId: EvaluationCaseId): EvaluationDatasetCaseV1? {
        if (caseId !in config.sampling.orderedCaseIds) return null
        return when (val load = loadSnapshot(config.dataset)) {
            is SnapshotLoad.Failed -> null
            is SnapshotLoad.Loaded -> load.snapshot.byId[caseId]
        }
    }

    fun categories(config: EvaluationRunConfig): List<EvaluationDatasetCategoryDefinition>? =
        when (val load = loadSnapshot(config.dataset)) {
            is SnapshotLoad.Failed -> null
            is SnapshotLoad.Loaded -> load.snapshot.categories
        }

    private fun loadSnapshot(identity: EvaluationDatasetIdentity): SnapshotLoad {
        cached?.takeIf { it.identity == identity }?.let { return SnapshotLoad.Loaded(it) }
        return synchronized(cacheLock) {
            cached?.takeIf { it.identity == identity }?.let { return@synchronized SnapshotLoad.Loaded(it) }
            val loaded = readSnapshot(identity)
            if (loaded is SnapshotLoad.Loaded) cached = loaded.snapshot
            loaded
        }
    }

    private fun readSnapshot(identity: EvaluationDatasetIdentity): SnapshotLoad {
        val pack = registry.find(identity.id, identity.version)
            ?: return SnapshotLoad.Failed(EvaluationFailureCode.DATASET_NOT_FOUND)
        if (pack.manifest.identity != identity) {
            return SnapshotLoad.Failed(EvaluationFailureCode.DATASET_DIGEST_MISMATCH)
        }

        val cases = try {
            File(pack.directory, CASES_FILE_NAME).inputStream().buffered().use(parser::parse)
        } catch (error: EvaluationDatasetParseException) {
            return SnapshotLoad.Failed(error.toFailureCode())
        } catch (_: Exception) {
            return SnapshotLoad.Failed(EvaluationFailureCode.DATASET_NOT_FOUND)
        }

        if (cases.size != pack.manifest.caseCount || cases.map { it.id }.distinct().size != cases.size) {
            return SnapshotLoad.Failed(EvaluationFailureCode.SAMPLE_SET_INVALID)
        }
        val knownCategories = pack.manifest.categories.mapTo(mutableSetOf()) { it.id }
        if (cases.any { it.categoryId !in knownCategories }) {
            return SnapshotLoad.Failed(EvaluationFailureCode.SAMPLE_SET_INVALID)
        }
        if (EvaluationDatasetContentDigester.verify(pack.manifest, cases) !is DatasetDigestVerification.Match) {
            return SnapshotLoad.Failed(EvaluationFailureCode.DATASET_DIGEST_MISMATCH)
        }

        return SnapshotLoad.Loaded(
            DatasetSnapshot(
                identity = identity,
                categories = pack.manifest.categories.toList(),
                byId = cases.associateBy { it.id },
            ),
        )
    }

    private companion object {
        const val CASES_FILE_NAME = "cases.jsonl"
    }
}

private data class DatasetSnapshot(
    val identity: EvaluationDatasetIdentity,
    val categories: List<EvaluationDatasetCategoryDefinition>,
    val byId: Map<EvaluationCaseId, EvaluationDatasetCaseV1>,
)

private sealed interface SnapshotLoad {
    data class Loaded(val snapshot: DatasetSnapshot) : SnapshotLoad

    data class Failed(val code: EvaluationFailureCode) : SnapshotLoad
}

private fun EvaluationDatasetParseException.toFailureCode(): EvaluationFailureCode = when (code) {
    DatasetParseErrorCode.UNSUPPORTED_SCHEMA -> EvaluationFailureCode.UNSUPPORTED_SCHEMA_VERSION
    else -> EvaluationFailureCode.SAMPLE_SET_INVALID
}

private fun preflightFailure(code: EvaluationFailureCode) = EvaluationFailure(
    stage = EvaluationFailureStage.PREFLIGHT,
    code = code,
)
