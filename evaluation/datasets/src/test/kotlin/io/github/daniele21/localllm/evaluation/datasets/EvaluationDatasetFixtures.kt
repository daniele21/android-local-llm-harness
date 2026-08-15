package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetOrigin
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswer
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswerKind
import io.github.daniele21.localllm.evaluation.EvaluationMessageRole
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.evaluators.ExactMatchEvaluator
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

internal object EvaluationDatasetFixtures {
    fun pack(caseCount: Int = 200): DatasetFixturePack {
        require(caseCount > 1) { "Fixture pack requires at least two cases" }
        val cases = (0 until caseCount).map { index ->
            val category = if (index % 2 == 0) REASONING else RETRIEVAL
            EvaluationDatasetCaseV1(
                id = EvaluationCaseId("case-${index.toString().padStart(3, '0')}"),
                categoryId = category,
                messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, "Return answer $index")),
                expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.TEXT, "answer-$index"),
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
        return pack(cases)
    }

    fun pack(cases: List<EvaluationDatasetCaseV1>): DatasetFixturePack {
        val manifest = EvaluationDatasetManifestV1(
            datasetId = EvaluationDatasetId("evaluation-fixture"),
            version = EvaluationDatasetVersion("1.0.0"),
            displayName = "Evaluation fixture",
            origin = EvaluationDatasetOrigin.BUILT_IN,
            caseCount = cases.size,
            contentDigest = EvaluationDatasetContentDigester.digest(cases),
            categories = listOf(
                EvaluationDatasetCategoryDefinition(REASONING, "Reasoning"),
                EvaluationDatasetCategoryDefinition(RETRIEVAL, "Retrieval"),
            ),
        )
        return DatasetFixturePack(manifest, cases)
    }

    data class DatasetFixturePack(val manifest: EvaluationDatasetManifestV1, val cases: List<EvaluationDatasetCaseV1>) {
        fun input(): InputStream = ByteArrayInputStream(
            cases.joinToString(separator = "") { EvaluationDatasetCanonicalJson.encodeCase(it) + "\n" }
                .toByteArray(StandardCharsets.UTF_8),
        )
    }

    private val REASONING = EvaluationCategoryId("reasoning")
    private val RETRIEVAL = EvaluationCategoryId("retrieval")
}
