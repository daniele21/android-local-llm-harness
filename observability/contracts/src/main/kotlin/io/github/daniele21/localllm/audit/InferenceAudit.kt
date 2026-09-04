package io.github.daniele21.localllm.audit

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConversationRole
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.UseCaseId

const val MAX_AUDIT_INPUT_CHARACTERS: Int = 32_768
const val MAX_AUDIT_EFFECTIVE_PROMPT_CHARACTERS: Int = 131_072
const val MAX_AUDIT_OUTPUT_CHARACTERS: Int = 262_144
const val MAX_AUDIT_MESSAGES: Int = 128
const val MAX_AUDIT_QUERY_LIMIT: Int = 200

enum class InferenceAuditOriginKind {
    HARNEX_INTERNAL,
    EXTERNAL_CONSUMER,
    EVALUATION,
    HEALTH_CHECK,
}

data class InferenceAuditOrigin(
    val kind: InferenceAuditOriginKind,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val verifiedPackageName: String? = null,
) {
    init {
        if (kind == InferenceAuditOriginKind.EXTERNAL_CONSUMER) {
            require(!verifiedPackageName.isNullOrBlank()) { "External audit origin requires a verified package name" }
        } else {
            require(verifiedPackageName == null) { "Only external audit origins may carry a verified package name" }
        }
        require(verifiedPackageName == null || verifiedPackageName.length <= 255) {
            "Verified package name must not exceed 255 characters"
        }
    }
}

sealed interface InferenceAuditInput {
    val characterCount: Int

    data class Text(val value: String) : InferenceAuditInput {
        init {
            validateAuditInput(value)
        }

        override val characterCount: Int = value.length

        override fun toString(): String = "Text(<redacted>, characters=$characterCount)"
    }

    data class Messages(val values: List<InferenceAuditMessage>) : InferenceAuditInput {
        init {
            require(values.isNotEmpty()) { "Audit messages must not be empty" }
            require(values.size <= MAX_AUDIT_MESSAGES) { "Audit messages must not exceed $MAX_AUDIT_MESSAGES entries" }
            require(values.sumOf { it.content.length } <= MAX_AUDIT_INPUT_CHARACTERS) {
                "Audit messages exceed $MAX_AUDIT_INPUT_CHARACTERS total characters"
            }
        }

        override val characterCount: Int = values.sumOf { it.content.length }

        override fun toString(): String = "Messages(<redacted>, count=${values.size}, characters=$characterCount)"
    }

    data class RawCompletion(val value: String) : InferenceAuditInput {
        init {
            validateAuditInput(value)
        }

        override val characterCount: Int = value.length

        override fun toString(): String = "RawCompletion(<redacted>, characters=$characterCount)"
    }
}

data class InferenceAuditMessage(val role: ConversationRole, val content: String) {
    init {
        validateAuditInput(content)
    }

    override fun toString(): String = "InferenceAuditMessage(role=$role, content=<redacted>, characters=${content.length})"
}

data class InferenceAuditAdmission(
    val requestId: RequestId,
    val origin: InferenceAuditOrigin,
    val receivedAtEpochMs: Long,
    val input: InferenceAuditInput,
) {
    init {
        require(receivedAtEpochMs >= 0) { "Audit admission timestamp must not be negative" }
    }

    override fun toString(): String =
        "InferenceAuditAdmission(requestId=$requestId, origin=$origin, receivedAtEpochMs=$receivedAtEpochMs, input=<redacted>)"
}

data class InferenceAuditPrepared(
    val requestId: RequestId,
    val preparedAtEpochMs: Long,
    val effectivePrompt: String?,
    val execution: InferenceAuditExecutionIdentity,
) {
    init {
        require(preparedAtEpochMs >= 0) { "Audit prepared timestamp must not be negative" }
        effectivePrompt?.let {
            require(it.isNotBlank()) { "Effective prompt must not be blank when present" }
            require('\u0000' !in it) { "Effective prompt must not contain NUL" }
            require(it.length <= MAX_AUDIT_EFFECTIVE_PROMPT_CHARACTERS) {
                "Effective prompt exceeds $MAX_AUDIT_EFFECTIVE_PROMPT_CHARACTERS characters"
            }
        }
    }

    override fun toString(): String =
        "InferenceAuditPrepared(requestId=$requestId, preparedAtEpochMs=$preparedAtEpochMs, " +
            "effectivePrompt=<redacted>, execution=$execution)"
}

data class InferenceAuditExecutionIdentity(
    val modelDigest: ModelDigest,
    val modelLoadKind: ModelLoadKind = ModelLoadKind.UNKNOWN,
    val presetId: String? = null,
    val presetVersion: Int? = null,
    val backendId: String? = null,
    val backendRevision: String? = null,
    val backendExecutionFingerprint: String? = null,
    val effectivePlacement: String? = null,
    val useCaseRevision: Int? = null,
    val bindingRevision: Int? = null,
) {
    init {
        require(presetId == null || presetId.isNotBlank()) { "Preset ID must not be blank" }
        require(presetVersion == null || presetVersion > 0) { "Preset version must be positive" }
        require(backendId == null || backendId.isNotBlank()) { "Backend ID must not be blank" }
        require(backendRevision == null || backendRevision.isNotBlank()) { "Backend revision must not be blank" }
        require(backendExecutionFingerprint == null || SHA256_PATTERN.matches(backendExecutionFingerprint)) {
            "Backend execution fingerprint must be SHA-256"
        }
        require(effectivePlacement == null || effectivePlacement.isNotBlank()) { "Effective placement must not be blank" }
        require(useCaseRevision == null || useCaseRevision > 0) { "Use-case revision must be positive" }
        require(bindingRevision == null || bindingRevision > 0) { "Binding revision must be positive" }
    }

    private companion object {
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

enum class InferenceAuditStatus {
    ADMITTED,
    PREPARED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    ;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED || this == INTERRUPTED
}

@JvmInline
value class InferenceAuditTerminalCode(val value: String) {
    init {
        require(CODE_PATTERN.matches(value)) {
            "Audit terminal code must contain only A-Z, 0-9 and underscore and be 1..64 characters"
        }
    }

    private companion object {
        val CODE_PATTERN = Regex("[A-Z0-9_]{1,64}")
    }
}

data class InferenceAuditMetrics(
    val queueMs: Long?,
    val modelLoadMs: Long?,
    val timeToFirstTokenMs: Long?,
    val totalMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val decodeTokensPerSecond: Double?,
    val prefillMs: Long? = null,
    val decodeMs: Long? = null,
    val modelLoadKind: ModelLoadKind = ModelLoadKind.UNKNOWN,
    val stopReason: StopReason = StopReason.UNKNOWN,
    val promptPlanningMs: Long? = null,
    val contextCreationMs: Long? = null,
    val timeToFirstAnswerMs: Long? = null,
    val reasoningTokens: Int? = null,
    val answerTokens: Int? = null,
) {
    init {
        listOf(
            queueMs,
            modelLoadMs,
            timeToFirstTokenMs,
            totalMs,
            prefillMs,
            decodeMs,
            promptPlanningMs,
            contextCreationMs,
            timeToFirstAnswerMs,
        ).forEach { value -> require(value == null || value >= 0) { "Audit metric durations must not be negative" } }
        listOf(inputTokens, outputTokens, reasoningTokens, answerTokens).forEach { value ->
            require(value == null || value >= 0) { "Audit token counts must not be negative" }
        }
        require(decodeTokensPerSecond == null || decodeTokensPerSecond.isFinite() && decodeTokensPerSecond >= 0.0) {
            "Audit throughput must be finite and non-negative"
        }
    }
}

data class InferenceAuditTerminalContent(
    val answerOutput: String = "",
    val reasoningOutput: String = "",
) {
    init {
        validateAuditOutput(answerOutput, "Answer output")
        validateAuditOutput(reasoningOutput, "Reasoning output")
    }

    override fun toString(): String =
        "InferenceAuditTerminalContent(answerOutput=<redacted:${answerOutput.length}>, " +
            "reasoningOutput=<redacted:${reasoningOutput.length}>)"
}

data class InferenceAuditTerminal(
    val requestId: RequestId,
    val status: InferenceAuditStatus,
    val completedAtEpochMs: Long,
    val content: InferenceAuditTerminalContent? = null,
    val metrics: InferenceAuditMetrics? = null,
    val terminalCode: InferenceAuditTerminalCode? = null,
) {
    init {
        require(status.isTerminal) { "Audit terminal update requires a terminal status" }
        require(completedAtEpochMs >= 0) { "Audit terminal timestamp must not be negative" }
        if (status == InferenceAuditStatus.COMPLETED) {
            require(content != null) { "Completed audit record requires terminal content" }
            require(metrics != null) { "Completed audit record requires metrics" }
            require(terminalCode == null) { "Completed audit record must not carry an error code" }
        } else {
            require(terminalCode != null) { "Non-success terminal audit record requires a terminal code" }
        }
    }

    override fun toString(): String =
        "InferenceAuditTerminal(requestId=$requestId, status=$status, completedAtEpochMs=$completedAtEpochMs, " +
            "content=<redacted>, metrics=$metrics, terminalCode=$terminalCode)"
}

data class InferenceAuditRecord(
    val admission: InferenceAuditAdmission,
    val status: InferenceAuditStatus,
    val prepared: InferenceAuditPrepared? = null,
    val runningAtEpochMs: Long? = null,
    val terminal: InferenceAuditTerminal? = null,
) {
    init {
        require(runningAtEpochMs == null || runningAtEpochMs >= 0) { "Audit running timestamp must not be negative" }
        require(prepared == null || prepared.requestId == admission.requestId) { "Prepared audit request ID must match admission" }
        require(terminal == null || terminal.requestId == admission.requestId) { "Terminal audit request ID must match admission" }
        require(status.isTerminal == (terminal != null)) { "Terminal status and terminal payload must agree" }
        require(status != InferenceAuditStatus.PREPARED || prepared != null) { "Prepared status requires prepared payload" }
        require(status != InferenceAuditStatus.RUNNING || runningAtEpochMs != null) { "Running status requires running timestamp" }
    }

    val requestId: RequestId
        get() = admission.requestId

    override fun toString(): String =
        "InferenceAuditRecord(requestId=$requestId, origin=${admission.origin}, status=$status, " +
            "prepared=${prepared != null}, runningAtEpochMs=$runningAtEpochMs, terminal=${terminal?.status}, content=<redacted>)"
}

data class InferenceAuditSummary(
    val requestId: RequestId,
    val origin: InferenceAuditOrigin,
    val status: InferenceAuditStatus,
    val receivedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val modelDigest: ModelDigest?,
    val totalMs: Long?,
    val decodeTokensPerSecond: Double?,
) {
    init {
        require(receivedAtEpochMs >= 0) { "Audit summary received timestamp must not be negative" }
        require(completedAtEpochMs == null || completedAtEpochMs >= receivedAtEpochMs) {
            "Audit summary completion must not precede admission"
        }
        require(totalMs == null || totalMs >= 0) { "Audit summary total duration must not be negative" }
        require(decodeTokensPerSecond == null || decodeTokensPerSecond.isFinite() && decodeTokensPerSecond >= 0.0) {
            "Audit summary throughput must be finite and non-negative"
        }
    }
}

data class InferenceAuditQuery(
    val limit: Int = 50,
    val applicationId: ApplicationId? = null,
    val useCaseId: UseCaseId? = null,
    val statuses: Set<InferenceAuditStatus> = emptySet(),
    val beforeReceivedAtEpochMs: Long? = null,
) {
    init {
        require(limit in 1..MAX_AUDIT_QUERY_LIMIT) { "Audit query limit must be in 1..$MAX_AUDIT_QUERY_LIMIT" }
        require(beforeReceivedAtEpochMs == null || beforeReceivedAtEpochMs >= 0) {
            "Audit query timestamp must not be negative"
        }
    }
}

data class InferenceAuditRetentionPolicy(
    val maxRecords: Int = 500,
    val maxAgeMs: Long = 30L * 24L * 60L * 60L * 1_000L,
    val maxEncryptedContentBytes: Long = 64L * 1_024L * 1_024L,
) {
    init {
        require(maxRecords > 0) { "Audit maxRecords must be positive" }
        require(maxAgeMs > 0) { "Audit maxAgeMs must be positive" }
        require(maxEncryptedContentBytes > 0) { "Audit maxEncryptedContentBytes must be positive" }
    }
}

enum class InferenceAuditFailureCode {
    UNAVAILABLE,
    ENCRYPTION_UNAVAILABLE,
    STORAGE_FAILURE,
    INVALID_STATE,
    NOT_FOUND,
    CORRUPT_CONTENT,
    CLOSED,
}

sealed interface InferenceAuditResult<out T> {
    data class Success<T>(val value: T) : InferenceAuditResult<T>

    data class Failure(val code: InferenceAuditFailureCode) : InferenceAuditResult<Nothing>
}

interface InferenceAuditRepository : AutoCloseable {
    fun admit(admission: InferenceAuditAdmission): InferenceAuditResult<Unit>

    fun markPrepared(prepared: InferenceAuditPrepared): InferenceAuditResult<Unit>

    fun markRunning(requestId: RequestId, runningAtEpochMs: Long): InferenceAuditResult<Unit>

    fun recordTerminal(terminal: InferenceAuditTerminal): InferenceAuditResult<Unit>

    fun recent(query: InferenceAuditQuery = InferenceAuditQuery()): InferenceAuditResult<List<InferenceAuditSummary>>

    fun find(requestId: RequestId): InferenceAuditResult<InferenceAuditRecord?>

    fun nonTerminal(limit: Int = 100): InferenceAuditResult<List<InferenceAuditRecord>>

    fun clearTerminalHistory(): InferenceAuditResult<Int>
}

private fun validateAuditInput(value: String) {
    require(value.isNotBlank()) { "Audit input must not be blank" }
    require('\u0000' !in value) { "Audit input must not contain NUL" }
    require(value.length <= MAX_AUDIT_INPUT_CHARACTERS) {
        "Audit input exceeds $MAX_AUDIT_INPUT_CHARACTERS characters"
    }
}

private fun validateAuditOutput(value: String, label: String) {
    require('\u0000' !in value) { "$label must not contain NUL" }
    require(value.length <= MAX_AUDIT_OUTPUT_CHARACTERS) {
        "$label exceeds $MAX_AUDIT_OUTPUT_CHARACTERS characters"
    }
}
