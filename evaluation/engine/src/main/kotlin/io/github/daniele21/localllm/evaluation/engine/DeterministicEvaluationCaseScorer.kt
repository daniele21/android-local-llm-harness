package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.evaluators.ExactMatchEvaluator
import io.github.daniele21.localllm.evaluation.evaluators.InstructionConstraintsEvaluator
import io.github.daniele21.localllm.evaluation.evaluators.JsonFieldsEvaluator
import io.github.daniele21.localllm.evaluation.evaluators.MultipleChoiceEvaluator
import io.github.daniele21.localllm.evaluation.evaluators.NumericFinalAnswerEvaluator
import io.github.daniele21.localllm.evaluation.evaluators.RegexFormatEvaluator

class DeterministicEvaluationCaseScorer(
    private val exactMatchEvaluator: ExactMatchEvaluator = ExactMatchEvaluator(),
    private val multipleChoiceEvaluator: MultipleChoiceEvaluator = MultipleChoiceEvaluator(),
    private val numericFinalAnswerEvaluator: NumericFinalAnswerEvaluator = NumericFinalAnswerEvaluator(),
    private val jsonFieldsEvaluator: JsonFieldsEvaluator = JsonFieldsEvaluator(),
    private val regexFormatEvaluator: RegexFormatEvaluator = RegexFormatEvaluator(),
    private val instructionConstraintsEvaluator: InstructionConstraintsEvaluator = InstructionConstraintsEvaluator(),
) {
    fun score(
        case: EvaluationDatasetCaseV1,
        requestId: RequestId,
        generated: String,
        metrics: EvaluationCaseMetrics = EvaluationCaseMetrics(),
    ): EvaluationCaseResult {
        val outcome = try {
            evaluate(case, generated)
        } catch (_: IllegalArgumentException) {
            return evaluatorFailure(case, requestId, metrics)
        } catch (_: IllegalStateException) {
            return evaluatorFailure(case, requestId, metrics)
        }

        val status = when (outcome.code) {
            EvaluatorOutcomeCode.INVALID_OUTPUT,
            EvaluatorOutcomeCode.AMBIGUOUS_OUTPUT,
            -> EvaluationCaseStatus.INVALID_OUTPUT

            else -> EvaluationCaseStatus.SCORED
        }
        return EvaluationCaseResult(
            caseId = case.id,
            categoryId = case.categoryId,
            evaluator = case.evaluator,
            status = status,
            outcome = outcome,
            requestId = requestId,
            metrics = metrics,
        )
    }

    private fun evaluate(case: EvaluationDatasetCaseV1, generated: String): EvaluationOutcome = when (case.evaluator.type) {
        EvaluatorType.EXACT_MATCH -> exactMatchEvaluator.evaluate(case.expected.value, generated, case.evaluator)

        EvaluatorType.MULTIPLE_CHOICE -> multipleChoiceEvaluator.evaluate(case.expected.value, generated, case.evaluator)

        EvaluatorType.NUMERIC_FINAL_ANSWER -> numericFinalAnswerEvaluator.evaluate(case.expected.value, generated, case.evaluator)

        EvaluatorType.JSON_FIELDS -> jsonFieldsEvaluator.evaluate(case.expected.value, generated, case.evaluator)

        EvaluatorType.REGEX_FORMAT -> regexFormatEvaluator.evaluate(generated, case.evaluator)

        EvaluatorType.INSTRUCTION_CONSTRAINTS -> instructionConstraintsEvaluator.evaluate(generated, case.evaluator)
    }

    private fun evaluatorFailure(
        case: EvaluationDatasetCaseV1,
        requestId: RequestId,
        metrics: EvaluationCaseMetrics,
    ): EvaluationCaseResult = EvaluationCaseResult(
        caseId = case.id,
        categoryId = case.categoryId,
        evaluator = case.evaluator,
        status = EvaluationCaseStatus.RUNTIME_FAILURE,
        outcome = null,
        requestId = requestId,
        metrics = metrics,
        failure = EvaluationFailure(
            stage = EvaluationFailureStage.EVALUATION,
            code = EvaluationFailureCode.EVALUATOR_FAILURE,
            caseId = case.id,
        ),
    )
}
