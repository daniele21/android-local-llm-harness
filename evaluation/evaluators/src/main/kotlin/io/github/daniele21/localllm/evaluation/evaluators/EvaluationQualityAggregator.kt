package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationCategoryScore
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationQualitySummary
import io.github.daniele21.localllm.evaluation.NormalizedScore

class EvaluationQualityAggregator {
    fun aggregate(
        categories: List<EvaluationDatasetCategoryDefinition>,
        caseResults: List<EvaluationCaseResult>,
    ): EvaluationQualitySummary {
        require(categories.isNotEmpty()) { "Quality aggregation requires declared categories" }
        require(categories.map { it.id }.distinct().size == categories.size) {
            "Quality aggregation category IDs must be unique"
        }
        require(caseResults.map { it.caseId }.distinct().size == caseResults.size) {
            "Quality aggregation case IDs must be unique"
        }

        val definitionsById = categories.associateBy { it.id }
        require(caseResults.all { it.categoryId in definitionsById }) {
            "Every evaluation result category must be declared by the dataset"
        }

        val categoryScores = categories.mapNotNull { category ->
            val contributions =
                caseResults
                    .asSequence()
                    .filter { it.categoryId == category.id }
                    .mapNotNull(::qualityContribution)
                    .toList()
            if (contributions.isEmpty()) {
                null
            } else {
                EvaluationCategoryScore(
                    categoryId = category.id,
                    score = NormalizedScore(contributions.sum() / contributions.size.toDouble()),
                    scoredCaseCount = contributions.size,
                    weight = category.weight,
                )
            }
        }

        return EvaluationQualitySummary(
            aggregateScore = aggregateCategoryScores(categoryScores),
            categoryScores = categoryScores,
        )
    }

    private fun qualityContribution(result: EvaluationCaseResult): Double? = when (result.status) {
        EvaluationCaseStatus.SCORED -> result.outcome!!.score.value
        EvaluationCaseStatus.INVALID_OUTPUT,
        EvaluationCaseStatus.TIMEOUT,
        EvaluationCaseStatus.RUNTIME_FAILURE,
        -> 0.0

        EvaluationCaseStatus.CANCELLED -> null
    }

    private fun aggregateCategoryScores(categoryScores: List<EvaluationCategoryScore>): NormalizedScore? {
        if (categoryScores.isEmpty()) return null
        val weighted = categoryScores.filter { it.weight != null }
        require(weighted.isEmpty() || weighted.size == categoryScores.size) {
            "Scored categories must either all declare weights or all omit weights"
        }
        val aggregate = if (weighted.isEmpty()) {
            categoryScores.sumOf { it.score.value } / categoryScores.size.toDouble()
        } else {
            val weightSum = categoryScores.sumOf { it.weight!! }
            categoryScores.sumOf { it.score.value * it.weight!! } / weightSum
        }
        return NormalizedScore(aggregate)
    }
}
