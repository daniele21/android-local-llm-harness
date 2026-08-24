package io.github.daniele21.localllm.evaluation.datasetadapter

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
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

/**
 * Production adapter from immutable, registry-published dataset packs to the runner's
 * preflight and case-definition ports. Every first load re-parses canonical JSONL and
 * re-verifies the manifest digest before any case is exposed to inference.
 */
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
                if (sampling.orderedCaseIds.all { it in load.snapshot.byId }) {
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

    fun categories(dataset: EvaluationDatasetIdentity): List<EvaluationDatasetCategoryDefinition>? =
        when (val load = loadSnapshot(dataset)) {
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
        return when (val cases = readCases(pack.directory)) {
            is CaseLoad.Failed -> SnapshotLoad.Failed(cases.code)
            is CaseLoad.Loaded -> validateCases(identity, pack.manifest, cases.cases)
        }
    }

    private fun readCases(directory: File): CaseLoad = try {
        CaseLoad.Loaded(
            File(directory, CASES_FILE_NAME).inputStream().buffered().use(parser::parse),
        )
    } catch (error: EvaluationDatasetParseException) {
        CaseLoad.Failed(error.toFailureCode())
    } catch (_: Exception) {
        CaseLoad.Failed(EvaluationFailureCode.DATASET_NOT_FOUND)
    }

    private fun validateCases(
        identity: EvaluationDatasetIdentity,
        manifest: EvaluationDatasetManifestV1,
        cases: List<EvaluationDatasetCaseV1>,
    ): SnapshotLoad {
        if (cases.size != manifest.caseCount || cases.map { it.id }.distinct().size != cases.size) {
            return SnapshotLoad.Failed(EvaluationFailureCode.SAMPLE_SET_INVALID)
        }
        val knownCategories = manifest.categories.mapTo(mutableSetOf()) { it.id }
        if (cases.any { it.categoryId !in knownCategories }) {
            return SnapshotLoad.Failed(EvaluationFailureCode.SAMPLE_SET_INVALID)
        }
        if (EvaluationDatasetContentDigester.verify(manifest, cases) !is DatasetDigestVerification.Match) {
            return SnapshotLoad.Failed(EvaluationFailureCode.DATASET_DIGEST_MISMATCH)
        }
        return SnapshotLoad.Loaded(
            DatasetSnapshot(
                identity = identity,
                categories = manifest.categories.toList(),
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

private sealed interface CaseLoad {
    data class Loaded(val cases: List<EvaluationDatasetCaseV1>) : CaseLoad
    data class Failed(val code: EvaluationFailureCode) : CaseLoad
}

private fun EvaluationDatasetParseException.toFailureCode(): EvaluationFailureCode = when (code) {
    DatasetParseErrorCode.UNSUPPORTED_SCHEMA -> EvaluationFailureCode.UNSUPPORTED_SCHEMA_VERSION
    else -> EvaluationFailureCode.SAMPLE_SET_INVALID
}

private fun preflightFailure(code: EvaluationFailureCode) = EvaluationFailure(
    stage = EvaluationFailureStage.PREFLIGHT,
    code = code,
)
