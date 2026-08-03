package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.observability.HealthFinding
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.SanityExecutionResult
import io.github.daniele21.localllm.observability.SanityExecutor
import io.github.daniele21.localllm.observability.SanityFixture
import io.github.daniele21.localllm.observability.SanityRule
import io.github.daniele21.localllm.observability.SanityRuleType
import io.github.daniele21.localllm.observability.SanitySuiteDefinition

internal class SanitySuiteRunner(
    private val sanityExecutor: SanityExecutor,
    private val monotonicClock: () -> Long,
) {
    fun run(definition: SanitySuiteDefinition): List<HealthFinding> =
        definition.fixtures.flatMap(::runFixture)

    private fun runFixture(fixture: SanityFixture): List<HealthFinding> {
        val execution = runCatching { sanityExecutor.execute(fixture) }
            .getOrElse { error ->
                SanityExecutionResult(
                    output = null,
                    outputTokens = null,
                    durationMs = 0L,
                    errorCode = "SANITY_EXECUTOR_FAILURE",
                    errorDetail = error.message,
                )
            }
        val executionFinding = if (execution.successful) {
            HealthFinding(
                id = "${fixture.id}.execution",
                status = HealthStatus.PASS,
                detail = "The deterministic sanity generation completed successfully.",
                durationMs = execution.durationMs,
            )
        } else {
            HealthFinding(
                id = "${fixture.id}.execution",
                status = HealthStatus.FAIL,
                detail = "The deterministic sanity generation failed with ${execution.errorCode ?: "UNKNOWN"}.",
                durationMs = execution.durationMs,
                remediation = "Inspect the model binding, runtime state and typed failure before retrying the fixture.",
            )
        }

        if (!execution.successful) {
            return listOf(executionFinding) + fixture.rules.map { rule ->
                notRun("${fixture.id}.${rule.id}")
            }
        }

        val output = requireNotNull(execution.output)
        return listOf(executionFinding) + fixture.rules.map { rule ->
            evaluateRule(fixture, rule, output, execution.outputTokens)
        }
    }

    private fun evaluateRule(
        fixture: SanityFixture,
        rule: SanityRule,
        output: String,
        outputTokens: Int?,
    ): HealthFinding {
        val startedAt = monotonicClock()
        val evaluation = when (rule.type) {
            SanityRuleType.NON_EMPTY -> ruleEvaluation(
                passed = output.isNotBlank(),
                passDetail = "The generated output is non-empty.",
                failDetail = "The generated output is empty.",
                remediation = "Inspect prompt construction, stop conditions and backend output conversion.",
            )

            SanityRuleType.CONTAINS -> ruleEvaluation(
                passed = output.contains(requireNotNull(rule.expectedText)),
                passDetail = "The generated output contains the required marker.",
                failDetail = "The generated output does not contain the required marker.",
                remediation = "Review the deterministic fixture, model version and output contract.",
            )

            SanityRuleType.NOT_CONTAINS -> ruleEvaluation(
                passed = !output.contains(requireNotNull(rule.expectedText)),
                passDetail = "The generated output excludes the forbidden marker.",
                failDetail = "The generated output contains a forbidden marker.",
                remediation = "Review prompt constraints, stop sequences and output validation.",
            )

            SanityRuleType.EXACT -> ruleEvaluation(
                passed = output == requireNotNull(rule.expectedText),
                passDetail = "The generated output matches the version-locked expectation.",
                failDetail = "The generated output differs from the version-locked expectation.",
                remediation = "Confirm that model digest, profile version and deterministic parameters are unchanged.",
            )

            SanityRuleType.MATCHES_REGEX -> regexEvaluation(
                output = output,
                pattern = requireNotNull(rule.expectedText),
            )

            SanityRuleType.MAX_OUTPUT_TOKENS -> tokenLimitEvaluation(
                outputTokens = outputTokens,
                maximum = requireNotNull(rule.expectedInt),
            )
        }
        return HealthFinding(
            id = "${fixture.id}.${rule.id}",
            status = evaluation.status,
            detail = evaluation.detail,
            durationMs = elapsedMillis(startedAt),
            remediation = evaluation.remediation,
        )
    }

    private fun regexEvaluation(output: String, pattern: String): RuleEvaluation = runCatching {
        Regex(pattern).matches(output)
    }.fold(
        onSuccess = { matches ->
            ruleEvaluation(
                passed = matches,
                passDetail = "The generated output matches the required structure.",
                failDetail = "The generated output does not match the required structure.",
                remediation = "Review the fixture regex, prompt template and deterministic model output.",
            )
        },
        onFailure = {
            RuleEvaluation(
                status = HealthStatus.FAIL,
                detail = "The configured sanity regex is invalid.",
                remediation = "Correct the fixture regex before rerunning the suite.",
            )
        },
    )

    private fun tokenLimitEvaluation(outputTokens: Int?, maximum: Int): RuleEvaluation = when {
        outputTokens == null -> RuleEvaluation(
            status = HealthStatus.FAIL,
            detail = "The runtime did not report an output token count.",
            remediation = "Verify generation metrics propagation before using token guardrails.",
        )

        outputTokens <= maximum -> RuleEvaluation(
            status = HealthStatus.PASS,
            detail = "The generated output respects the configured token guardrail.",
        )

        else -> RuleEvaluation(
            status = HealthStatus.FAIL,
            detail = "The generated output exceeds the configured token guardrail.",
            remediation = "Inspect max-output enforcement and stop-condition handling.",
        )
    }

    private fun ruleEvaluation(
        passed: Boolean,
        passDetail: String,
        failDetail: String,
        remediation: String,
    ): RuleEvaluation = if (passed) {
        RuleEvaluation(
            status = HealthStatus.PASS,
            detail = passDetail,
        )
    } else {
        RuleEvaluation(
            status = HealthStatus.FAIL,
            detail = failDetail,
            remediation = remediation,
        )
    }

    private fun notRun(id: String): HealthFinding = HealthFinding(
        id = id,
        status = HealthStatus.NOT_RUN,
        detail = "The assertion was not evaluated because generation did not complete.",
        durationMs = 0L,
        remediation = "Resolve the fixture execution failure, then rerun the suite.",
    )

    private fun elapsedMillis(startedAtNanos: Long): Long =
        (monotonicClock() - startedAtNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND

    private data class RuleEvaluation(
        val status: HealthStatus,
        val detail: String,
        val remediation: String? = null,
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
