package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthControlPlane
import io.github.daniele21.localllm.observability.HealthFinding
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.HealthSuiteReport
import io.github.daniele21.localllm.observability.ModelIntegrityTarget
import io.github.daniele21.localllm.observability.SanityExecutionResult
import io.github.daniele21.localllm.observability.SanityExecutor
import io.github.daniele21.localllm.observability.SanityFixture
import io.github.daniele21.localllm.observability.SanityRule
import io.github.daniele21.localllm.observability.SanityRuleType
import io.github.daniele21.localllm.observability.SanitySuiteDefinition
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.StoredModel

class HealthEngine(
    private val modelStore: ModelStore,
    private val telemetryRepository: TelemetryRepository,
    private val sanityExecutor: SanityExecutor,
    private val epochClock: () -> Long = System::currentTimeMillis,
    private val monotonicClock: () -> Long = System::nanoTime,
) : HealthControlPlane {
    override fun runModelIntegrity(target: ModelIntegrityTarget): HealthSuiteReport {
        val startedAt = epochClock()
        val findings = mutableListOf<HealthFinding>()
        val stored = timedFinding("model.present") {
            modelStore.find(target.digest)?.let {
                FindingOutcome(
                    status = HealthStatus.PASS,
                    detail = "The expected model digest is registered in the model store.",
                    value = it,
                )
            } ?: FindingOutcome(
                status = HealthStatus.FAIL,
                detail = "The expected model digest is not registered in the model store.",
                remediation = "Import the exact GGUF artifact before preparing this model profile.",
                value = null,
            )
        }
        findings += stored.finding

        val model = stored.value
        if (model == null) {
            findings += notRun(
                id = "model.file",
                detail = "File checks require a registered model-store entry.",
                remediation = "Restore or import the expected content-addressed model artifact.",
            )
            findings += notRun(
                id = "model.size",
                detail = "Size validation requires a registered model-store entry.",
                remediation = "Restore or import the expected content-addressed model artifact.",
            )
            findings += notRun(
                id = "model.digest",
                detail = "Digest verification requires a registered model-store entry.",
                remediation = "Restore or import the expected content-addressed model artifact.",
            )
            findings += notRun(
                id = "model.snapshot",
                detail = "Snapshot consistency requires a registered model-store entry.",
                remediation = "Restore or import the expected content-addressed model artifact.",
            )
        } else {
            findings += fileFinding(model)
            findings += sizeFinding(model, target.expectedSizeBytes)
            findings += digestFinding(target)
            findings += snapshotFinding(model)
        }

        return completeReport(
            suiteId = "model-integrity:${target.id}",
            startedAt = startedAt,
            findings = findings,
        )
    }

    override fun runSanitySuite(definition: SanitySuiteDefinition): HealthSuiteReport {
        val startedAt = epochClock()
        val findings = definition.fixtures.flatMap(::runSanityFixture)
        return completeReport(
            suiteId = "sanity:${definition.id}",
            startedAt = startedAt,
            findings = findings,
        )
    }

    override fun latestResults(): List<HealthCheckResult> = runCatching {
        telemetryRepository.healthResults()
    }.getOrDefault(emptyList())

    private fun runSanityFixture(fixture: SanityFixture): List<HealthFinding> {
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
                notRun(
                    id = "${fixture.id}.${rule.id}",
                    detail = "The assertion was not evaluated because generation did not complete.",
                    remediation = "Resolve the fixture execution failure, then rerun the suite.",
                )
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
        val outcome = when (rule.type) {
            SanityRuleType.NON_EMPTY -> ruleOutcome(
                passed = output.isNotBlank(),
                passDetail = "The generated output is non-empty.",
                failDetail = "The generated output is empty.",
                remediation = "Inspect prompt construction, stop conditions and backend output conversion.",
            )

            SanityRuleType.CONTAINS -> ruleOutcome(
                passed = output.contains(requireNotNull(rule.expectedText)),
                passDetail = "The generated output contains the required marker.",
                failDetail = "The generated output does not contain the required marker.",
                remediation = "Review the deterministic fixture, model version and output contract.",
            )

            SanityRuleType.NOT_CONTAINS -> ruleOutcome(
                passed = !output.contains(requireNotNull(rule.expectedText)),
                passDetail = "The generated output excludes the forbidden marker.",
                failDetail = "The generated output contains a forbidden marker.",
                remediation = "Review prompt constraints, stop sequences and output validation.",
            )

            SanityRuleType.EXACT -> ruleOutcome(
                passed = output == requireNotNull(rule.expectedText),
                passDetail = "The generated output matches the version-locked expectation.",
                failDetail = "The generated output differs from the version-locked expectation.",
                remediation = "Confirm that model digest, profile version and deterministic parameters are unchanged.",
            )

            SanityRuleType.MATCHES_REGEX -> regexOutcome(output, requireNotNull(rule.expectedText))
            SanityRuleType.MAX_OUTPUT_TOKENS -> tokenLimitOutcome(outputTokens, requireNotNull(rule.expectedInt))
        }
        return HealthFinding(
            id = "${fixture.id}.${rule.id}",
            status = outcome.status,
            detail = outcome.detail,
            remediation = outcome.remediation,
            durationMs = elapsedMillis(startedAt),
        )
    }

    private fun regexOutcome(output: String, pattern: String): FindingOutcome<Unit> = runCatching {
        Regex(pattern).matches(output)
    }.fold(
        onSuccess = { matches ->
            ruleOutcome(
                passed = matches,
                passDetail = "The generated output matches the required structure.",
                failDetail = "The generated output does not match the required structure.",
                remediation = "Review the fixture regex, prompt template and deterministic model output.",
            )
        },
        onFailure = {
            FindingOutcome(
                status = HealthStatus.FAIL,
                detail = "The configured sanity regex is invalid.",
                remediation = "Correct the fixture regex before rerunning the suite.",
                value = Unit,
            )
        },
    )

    private fun tokenLimitOutcome(outputTokens: Int?, maximum: Int): FindingOutcome<Unit> = when {
        outputTokens == null -> FindingOutcome(
            status = HealthStatus.FAIL,
            detail = "The runtime did not report an output token count.",
            remediation = "Verify generation metrics propagation before using token guardrails.",
            value = Unit,
        )

        outputTokens <= maximum -> FindingOutcome(
            status = HealthStatus.PASS,
            detail = "The generated output respects the configured token guardrail.",
            value = Unit,
        )

        else -> FindingOutcome(
            status = HealthStatus.FAIL,
            detail = "The generated output exceeds the configured token guardrail.",
            remediation = "Inspect max-output enforcement and stop-condition handling.",
            value = Unit,
        )
    }

    private fun fileFinding(model: StoredModel): HealthFinding = timedFinding("model.file") {
        if (model.file.isFile) {
            FindingOutcome(
                status = HealthStatus.PASS,
                detail = "The content-addressed GGUF file exists and is a regular file.",
                value = Unit,
            )
        } else {
            FindingOutcome(
                status = HealthStatus.FAIL,
                detail = "The content-addressed model path is missing or is not a regular file.",
                remediation = "Remove the stale store entry and import the expected GGUF again.",
                value = Unit,
            )
        }
    }.finding

    private fun sizeFinding(model: StoredModel, expectedSizeBytes: Long): HealthFinding = timedFinding("model.size") {
        val actualSize = model.file.takeIf { it.isFile }?.length() ?: model.sizeBytes
        if (actualSize == expectedSizeBytes && model.sizeBytes == expectedSizeBytes) {
            FindingOutcome(
                status = HealthStatus.PASS,
                detail = "The stored and on-disk model sizes match the declared artifact size.",
                value = Unit,
            )
        } else {
            FindingOutcome(
                status = HealthStatus.FAIL,
                detail = "The stored or on-disk model size differs from the declared artifact size.",
                remediation = "Quarantine the artifact and import a file matching the declared digest and size.",
                value = Unit,
            )
        }
    }.finding

    private fun digestFinding(target: ModelIntegrityTarget): HealthFinding = timedFinding("model.digest") {
        val verification = modelStore.verify(target.digest)
        if (verification.valid && verification.actualDigest == target.digest) {
            FindingOutcome(
                status = HealthStatus.PASS,
                detail = "Streaming SHA-256 verification matches the expected model digest.",
                value = Unit,
            )
        } else {
            FindingOutcome(
                status = HealthStatus.FAIL,
                detail = "Streaming SHA-256 verification does not match the expected model digest.",
                remediation = "Remove or quarantine the corrupted artifact and reimport the exact GGUF.",
                value = Unit,
            )
        }
    }.finding

    private fun snapshotFinding(model: StoredModel): HealthFinding = timedFinding("model.snapshot") {
        val snapshotEntry = modelStore.snapshot().entries.firstOrNull { it.digest == model.digest }
        if (snapshotEntry != null && snapshotEntry.file == model.file && snapshotEntry.sizeBytes == model.sizeBytes) {
            FindingOutcome(
                status = HealthStatus.PASS,
                detail = "The model-store snapshot is consistent with the resolved artifact entry.",
                value = Unit,
            )
        } else {
            FindingOutcome(
                status = HealthStatus.WARN,
                detail = "The model-store snapshot is inconsistent with the resolved artifact entry.",
                remediation = "Rebuild store metadata and verify no stale or duplicate entry remains.",
                value = Unit,
            )
        }
    }.finding

    private fun completeReport(
        suiteId: String,
        startedAt: Long,
        findings: List<HealthFinding>,
    ): HealthSuiteReport {
        findings.forEach { finding -> persistFinding(suiteId, finding) }
        val completedAt = epochClock().coerceAtLeast(startedAt)
        return HealthSuiteReport(
            suiteId = suiteId,
            startedAtEpochMs = startedAt,
            completedAtEpochMs = completedAt,
            status = aggregateStatus(findings),
            findings = findings,
        )
    }

    private fun persistFinding(suiteId: String, finding: HealthFinding) {
        val persistedDetail = buildString {
            append(finding.detail)
            finding.remediation?.let { remediation ->
                append(" Remediation: ")
                append(remediation)
            }
        }
        runCatching {
            telemetryRepository.saveHealth(
                HealthCheckResult(
                    id = "$suiteId:${finding.id}",
                    status = finding.status,
                    detail = persistedDetail,
                    durationMs = finding.durationMs,
                ),
            )
        }
    }

    private fun aggregateStatus(findings: List<HealthFinding>): HealthStatus = when {
        findings.any { it.status == HealthStatus.FAIL } -> HealthStatus.FAIL
        findings.any { it.status == HealthStatus.WARN } -> HealthStatus.WARN
        findings.all { it.status == HealthStatus.NOT_RUN } -> HealthStatus.NOT_RUN
        findings.any { it.status == HealthStatus.NOT_RUN } -> HealthStatus.WARN
        else -> HealthStatus.PASS
    }

    private fun notRun(id: String, detail: String, remediation: String): HealthFinding = HealthFinding(
        id = id,
        status = HealthStatus.NOT_RUN,
        detail = detail,
        durationMs = 0L,
        remediation = remediation,
    )

    private fun ruleOutcome(
        passed: Boolean,
        passDetail: String,
        failDetail: String,
        remediation: String,
    ): FindingOutcome<Unit> = if (passed) {
        FindingOutcome(
            status = HealthStatus.PASS,
            detail = passDetail,
            value = Unit,
        )
    } else {
        FindingOutcome(
            status = HealthStatus.FAIL,
            detail = failDetail,
            remediation = remediation,
            value = Unit,
        )
    }

    private fun <T> timedFinding(id: String, block: () -> FindingOutcome<T>): TimedFinding<T> {
        val startedAt = monotonicClock()
        val outcome = runCatching(block).getOrElse { error ->
            FindingOutcome(
                status = HealthStatus.FAIL,
                detail = "The health check failed unexpectedly.",
                remediation = "Inspect the diagnostic logs for ${error::class.java.simpleName} and retry the check.",
                value = null,
            )
        }
        return TimedFinding(
            finding = HealthFinding(
                id = id,
                status = outcome.status,
                detail = outcome.detail,
                durationMs = elapsedMillis(startedAt),
                remediation = outcome.remediation,
            ),
            value = outcome.value,
        )
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        ((monotonicClock() - startedAtNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)

    private data class FindingOutcome<T>(
        val status: HealthStatus,
        val detail: String,
        val remediation: String? = null,
        val value: T?,
    )

    private data class TimedFinding<T>(
        val finding: HealthFinding,
        val value: T?,
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
