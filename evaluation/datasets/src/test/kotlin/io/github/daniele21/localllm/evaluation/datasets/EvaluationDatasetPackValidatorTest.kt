package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetOrigin
import io.github.daniele21.localllm.evaluation.EvaluationDatasetPresetDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswer
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswerKind
import io.github.daniele21.localllm.evaluation.EvaluationMessageRole
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorRegistry
import io.github.daniele21.localllm.evaluation.evaluators.ExactMatchEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationDatasetPackValidatorTest {
    private val registry = EvaluatorRegistry(listOf(ExactMatchEvaluator.REGISTRATION))
    private val validator = EvaluationDatasetPackValidator(registry)

    @Test
    fun validPackPasses() {
        val cases = listOf(case("case-1", "reasoning"))
        val manifest = manifest(caseCount = 1)

        assertSame(DatasetValidationResult.Valid, validator.validate(manifest, cases))
    }

    @Test
    fun duplicateCaseAndUnknownCategoryAreReportedTogether() {
        val cases = listOf(case("case-1", "reasoning"), case("case-1", "missing"))
        val result = validator.validate(manifest(caseCount = 2), cases) as DatasetValidationResult.Invalid

        assertTrue(result.issues.any { it.code == DatasetValidationIssueCode.DUPLICATE_CASE_ID })
        assertTrue(result.issues.any { it.code == DatasetValidationIssueCode.UNKNOWN_CATEGORY })
    }

    @Test
    fun unsupportedEvaluatorIsRejectedThroughRegistry() {
        val unsupported = case("case-1", "reasoning").copy(
            evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(2)),
        )
        val result = validator.validate(manifest(caseCount = 1), listOf(unsupported)) as DatasetValidationResult.Invalid

        assertEquals(DatasetValidationIssueCode.UNSUPPORTED_EVALUATOR, result.issues.single().code)
    }

    @Test
    fun presetCannotReferenceUnknownCase() {
        val manifest = manifest(
            caseCount = 1,
            presets = listOf(EvaluationDatasetPresetDefinition("standard", listOf(EvaluationCaseId("missing")))),
        )
        val result = validator.validate(manifest, listOf(case("case-1", "reasoning"))) as DatasetValidationResult.Invalid

        assertEquals(DatasetValidationIssueCode.UNKNOWN_PRESET_CASE, result.issues.single().code)
        assertEquals("standard", result.issues.single().presetId)
    }

    @Test
    fun mixedWeightedAndUnweightedCategoriesFailClosed() {
        val manifest = manifest(
            caseCount = 1,
            categories = listOf(
                EvaluationDatasetCategoryDefinition(EvaluationCategoryId("reasoning"), "Reasoning", weight = 0.7),
                EvaluationDatasetCategoryDefinition(EvaluationCategoryId("format"), "Format"),
            ),
        )
        val result = validator.validate(manifest, listOf(case("case-1", "reasoning"))) as DatasetValidationResult.Invalid

        assertTrue(result.issues.any { it.code == DatasetValidationIssueCode.MIXED_CATEGORY_WEIGHT_POLICY })
    }

    private fun manifest(
        caseCount: Int,
        categories: List<EvaluationDatasetCategoryDefinition> = listOf(
            EvaluationDatasetCategoryDefinition(EvaluationCategoryId("reasoning"), "Reasoning"),
        ),
        presets: List<EvaluationDatasetPresetDefinition> = emptyList(),
    ) = EvaluationDatasetManifestV1(
        datasetId = EvaluationDatasetId("fixture"),
        version = EvaluationDatasetVersion("1.0.0"),
        displayName = "Fixture",
        origin = EvaluationDatasetOrigin.BUILT_IN,
        caseCount = caseCount,
        contentDigest = EvaluationDatasetDigest("1".repeat(64)),
        categories = categories,
        presets = presets,
    )

    private fun case(id: String, category: String) = EvaluationDatasetCaseV1(
        id = EvaluationCaseId(id),
        categoryId = EvaluationCategoryId(category),
        messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, "Answer alpha")),
        expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.TEXT, "alpha"),
        evaluator = EvaluatorSpec(
            type = EvaluatorType.EXACT_MATCH,
            version = ExactMatchEvaluator.VERSION,
            parameters = mapOf(
                ExactMatchEvaluator.PARAM_CASE to ExactMatchEvaluator.CASE_SENSITIVE,
                ExactMatchEvaluator.PARAM_WHITESPACE to ExactMatchEvaluator.WHITESPACE_TRIM,
            ),
        ),
    )
}
