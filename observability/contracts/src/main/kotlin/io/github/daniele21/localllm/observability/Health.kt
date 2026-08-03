package io.github.daniele21.localllm.observability

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId

data class ModelIntegrityTarget(val id: String, val digest: ModelDigest, val expectedSizeBytes: Long) {
    init {
        require(id.isNotBlank()) { "Model integrity target id must not be blank" }
        require(expectedSizeBytes >= 0L) { "Expected model size must not be negative" }
    }
}

data class SanitySuiteDefinition(val id: String, val fixtures: List<SanityFixture>) {
    init {
        require(id.isNotBlank()) { "Sanity suite id must not be blank" }
        require(fixtures.isNotEmpty()) { "Sanity suite must contain at least one fixture" }
        require(fixtures.map { it.id }.distinct().size == fixtures.size) {
            "Sanity fixture ids must be unique within a suite"
        }
    }
}

data class SanityFixture(
    val id: String,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val input: String,
    val generation: SanityGenerationConfig = SanityGenerationConfig(),
    val rules: List<SanityRule> = listOf(SanityRule.nonEmpty("non-empty")),
    val timeoutMs: Long = 30_000L,
) {
    init {
        require(id.isNotBlank()) { "Sanity fixture id must not be blank" }
        require(input.isNotBlank()) { "Sanity fixture input must not be blank" }
        require(rules.isNotEmpty()) { "Sanity fixture must contain at least one rule" }
        require(rules.map { it.id }.distinct().size == rules.size) {
            "Sanity rule ids must be unique within a fixture"
        }
        require(timeoutMs > 0L) { "Sanity fixture timeout must be positive" }
    }
}

data class SanityGenerationConfig(val maxOutputTokens: Int = 64, val temperature: Float = 0.0f, val seed: Long = 0L) {
    init {
        require(maxOutputTokens > 0) { "Sanity maxOutputTokens must be positive" }
        require(temperature >= 0.0f) { "Sanity temperature must not be negative" }
    }
}

data class SanityRule(val id: String, val type: SanityRuleType, val expectedText: String? = null, val expectedInt: Int? = null) {
    init {
        require(id.isNotBlank()) { "Sanity rule id must not be blank" }
        when (type) {
            SanityRuleType.NON_EMPTY -> Unit

            SanityRuleType.CONTAINS,
            SanityRuleType.NOT_CONTAINS,
            SanityRuleType.EXACT,
            SanityRuleType.MATCHES_REGEX,
            -> require(!expectedText.isNullOrEmpty()) { "$type requires expectedText" }

            SanityRuleType.MAX_OUTPUT_TOKENS -> require(expectedInt != null && expectedInt >= 0) {
                "$type requires a non-negative expectedInt"
            }
        }
    }

    companion object {
        fun nonEmpty(id: String): SanityRule = SanityRule(id, SanityRuleType.NON_EMPTY)

        fun contains(id: String, text: String): SanityRule = SanityRule(
            id = id,
            type = SanityRuleType.CONTAINS,
            expectedText = text,
        )

        fun notContains(id: String, text: String): SanityRule = SanityRule(
            id = id,
            type = SanityRuleType.NOT_CONTAINS,
            expectedText = text,
        )

        fun exact(id: String, text: String): SanityRule = SanityRule(
            id = id,
            type = SanityRuleType.EXACT,
            expectedText = text,
        )

        fun matchesRegex(id: String, pattern: String): SanityRule = SanityRule(
            id = id,
            type = SanityRuleType.MATCHES_REGEX,
            expectedText = pattern,
        )

        fun maxOutputTokens(id: String, maximum: Int): SanityRule = SanityRule(
            id = id,
            type = SanityRuleType.MAX_OUTPUT_TOKENS,
            expectedInt = maximum,
        )
    }
}

enum class SanityRuleType {
    NON_EMPTY,
    CONTAINS,
    NOT_CONTAINS,
    EXACT,
    MATCHES_REGEX,
    MAX_OUTPUT_TOKENS,
}

data class SanityExecutionResult(
    val output: String?,
    val outputTokens: Int?,
    val durationMs: Long,
    val errorCode: String? = null,
    val errorDetail: String? = null,
) {
    val successful: Boolean
        get() = errorCode == null && output != null
}

fun interface SanityExecutor {
    fun execute(fixture: SanityFixture): SanityExecutionResult
}

data class HealthFinding(
    val id: String,
    val status: HealthStatus,
    val detail: String,
    val durationMs: Long,
    val remediation: String? = null,
)

data class HealthSuiteReport(
    val suiteId: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val status: HealthStatus,
    val findings: List<HealthFinding>,
) {
    init {
        require(suiteId.isNotBlank()) { "Health suite id must not be blank" }
        require(completedAtEpochMs >= startedAtEpochMs) {
            "Health suite completion time must not precede its start time"
        }
    }
}

interface HealthControlPlane {
    fun runModelIntegrity(target: ModelIntegrityTarget): HealthSuiteReport

    fun runSanitySuite(definition: SanitySuiteDefinition): HealthSuiteReport

    fun latestResults(): List<HealthCheckResult>
}
