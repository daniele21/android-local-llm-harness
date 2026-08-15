package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorLookupResult
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorRegistry

enum class DatasetValidationIssueCode {
    CASE_COUNT_MISMATCH,
    DUPLICATE_CASE_ID,
    UNKNOWN_CATEGORY,
    UNSUPPORTED_EVALUATOR,
    UNKNOWN_PRESET_CASE,
    MIXED_CATEGORY_WEIGHT_POLICY,
}

data class DatasetValidationIssue(val code: DatasetValidationIssueCode, val caseId: EvaluationCaseId? = null, val presetId: String? = null)

sealed interface DatasetValidationResult {
    data object Valid : DatasetValidationResult

    data class Invalid(val issues: List<DatasetValidationIssue>) : DatasetValidationResult {
        init {
            require(issues.isNotEmpty()) { "Invalid dataset result must contain at least one issue" }
        }
    }
}

class EvaluationDatasetPackValidator(private val evaluatorRegistry: EvaluatorRegistry, private val maxIssues: Int = DEFAULT_MAX_ISSUES) {
    init {
        require(maxIssues > 0) { "Dataset validator issue bound must be positive" }
    }

    fun validate(manifest: EvaluationDatasetManifestV1, cases: List<EvaluationDatasetCaseV1>): DatasetValidationResult {
        val collector = ValidationIssueCollector(maxIssues)
        if (manifest.caseCount != cases.size) {
            collector.add(DatasetValidationIssue(DatasetValidationIssueCode.CASE_COUNT_MISMATCH))
        }
        validateWeightPolicy(manifest, collector)
        validateCases(manifest, cases, collector)
        validatePresets(manifest, cases, collector)
        return collector.toResult()
    }

    private fun validateCases(
        manifest: EvaluationDatasetManifestV1,
        cases: List<EvaluationDatasetCaseV1>,
        collector: ValidationIssueCollector,
    ) {
        val knownCategories = manifest.categories.mapTo(mutableSetOf()) { it.id }
        val seenCaseIds = mutableSetOf<EvaluationCaseId>()
        for (case in cases) {
            if (!collector.canAcceptMore) return
            if (!seenCaseIds.add(case.id)) {
                collector.add(DatasetValidationIssue(DatasetValidationIssueCode.DUPLICATE_CASE_ID, caseId = case.id))
            }
            if (case.categoryId !in knownCategories) {
                collector.add(DatasetValidationIssue(DatasetValidationIssueCode.UNKNOWN_CATEGORY, caseId = case.id))
            }
            if (evaluatorRegistry.resolve(case.evaluator) !is EvaluatorLookupResult.Supported) {
                collector.add(DatasetValidationIssue(DatasetValidationIssueCode.UNSUPPORTED_EVALUATOR, caseId = case.id))
            }
        }
    }

    private fun validatePresets(
        manifest: EvaluationDatasetManifestV1,
        cases: List<EvaluationDatasetCaseV1>,
        collector: ValidationIssueCollector,
    ) {
        val knownCases = cases.mapTo(mutableSetOf()) { it.id }
        for (preset in manifest.presets) {
            for (caseId in preset.orderedCaseIds) {
                if (!collector.canAcceptMore) return
                if (caseId !in knownCases) {
                    collector.add(
                        DatasetValidationIssue(
                            code = DatasetValidationIssueCode.UNKNOWN_PRESET_CASE,
                            caseId = caseId,
                            presetId = preset.id,
                        ),
                    )
                }
            }
        }
    }

    private fun validateWeightPolicy(manifest: EvaluationDatasetManifestV1, collector: ValidationIssueCollector) {
        val weighted = manifest.categories.count { it.weight != null }
        if (weighted != 0 && weighted != manifest.categories.size) {
            collector.add(DatasetValidationIssue(DatasetValidationIssueCode.MIXED_CATEGORY_WEIGHT_POLICY))
        }
    }

    private companion object {
        const val DEFAULT_MAX_ISSUES = 64
    }
}

private class ValidationIssueCollector(private val limit: Int) {
    private val mutableIssues = mutableListOf<DatasetValidationIssue>()

    val canAcceptMore: Boolean
        get() = mutableIssues.size < limit

    fun add(issue: DatasetValidationIssue) {
        if (canAcceptMore) mutableIssues += issue
    }

    fun toResult(): DatasetValidationResult = if (mutableIssues.isEmpty()) {
        DatasetValidationResult.Valid
    } else {
        DatasetValidationResult.Invalid(mutableIssues.toList())
    }
}
