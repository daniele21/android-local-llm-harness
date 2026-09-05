package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.audit.InferenceAuditFailureCode
import io.github.daniele21.localllm.audit.InferenceAuditInput
import io.github.daniele21.localllm.audit.InferenceAuditQuery
import io.github.daniele21.localllm.audit.InferenceAuditRecord
import io.github.daniele21.localllm.audit.InferenceAuditRepository
import io.github.daniele21.localllm.audit.InferenceAuditResult
import io.github.daniele21.localllm.audit.InferenceAuditStatus
import io.github.daniele21.localllm.audit.InferenceAuditSummary
import io.github.daniele21.localllm.audit.InferenceAuditTerminal
import io.github.daniele21.localllm.audit.InferenceAuditTerminalCode
import io.github.daniele21.localllm.audit.MAX_AUDIT_QUERY_LIMIT
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId

internal data class InferenceActivityListItem(
    val requestId: String,
    val applicationLabel: String,
    val applicationId: String,
    val verifiedPackageName: String?,
    val useCaseId: String,
    val status: InferenceAuditStatus,
    val receivedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val modelDigest: String?,
    val totalMs: Long?,
    val decodeTokensPerSecond: Double?,
)

internal data class InferenceActivityDetail(
    val requestId: String,
    val applicationLabel: String,
    val applicationId: String,
    val verifiedPackageName: String?,
    val useCaseId: String,
    val status: InferenceAuditStatus,
    val receivedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val input: String,
    val effectivePrompt: String?,
    val answerOutput: String?,
    val reasoningOutput: String?,
    val modelDigest: String?,
    val modelLoadKind: String?,
    val presetId: String?,
    val presetVersion: Int?,
    val backendId: String?,
    val backendRevision: String?,
    val backendExecutionFingerprint: String?,
    val effectivePlacement: String?,
    val queueMs: Long?,
    val modelLoadMs: Long?,
    val timeToFirstTokenMs: Long?,
    val timeToFirstAnswerMs: Long?,
    val totalMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val reasoningTokens: Int?,
    val answerTokens: Int?,
    val decodeTokensPerSecond: Double?,
    val prefillMs: Long?,
    val decodeMs: Long?,
    val promptPlanningMs: Long?,
    val contextCreationMs: Long?,
    val stopReason: String?,
    val terminalCode: String?,
)

internal enum class InferenceActivityPeriod(val displayLabel: String, private val windowMs: Long?) {
    ALL("All time", null),
    LAST_24_HOURS("24 hours", 24L * 60L * 60L * 1_000L),
    LAST_7_DAYS("7 days", 7L * 24L * 60L * 60L * 1_000L),
    LAST_30_DAYS("30 days", 30L * 24L * 60L * 60L * 1_000L),
    ;

    fun lowerBound(nowEpochMs: Long): Long? = windowMs?.let { (nowEpochMs - it).coerceAtLeast(0L) }
}

internal data class InferenceActivityFilter(
    val applicationId: String? = null,
    val status: InferenceAuditStatus? = null,
    val period: InferenceActivityPeriod = InferenceActivityPeriod.ALL,
    val useCaseId: String? = null,
) {
    val isDefault: Boolean
        get() = applicationId == null && status == null && period == InferenceActivityPeriod.ALL && useCaseId == null
}

internal data class InferenceActivityFilterOption(val id: String, val label: String)

internal data class InferenceActivityFilterOptions(
    val applications: List<InferenceActivityFilterOption> = emptyList(),
    val useCases: List<InferenceActivityFilterOption> = emptyList(),
)

internal data class InferenceActivityUiState(
    val items: List<InferenceActivityListItem> = emptyList(),
    val filterOptions: InferenceActivityFilterOptions = InferenceActivityFilterOptions(),
    val hasTerminalHistory: Boolean = false,
    val errorCode: InferenceAuditFailureCode? = null,
)

internal sealed interface InferenceActivityDetailResult {
    data class Available(val detail: InferenceActivityDetail) : InferenceActivityDetailResult
    data class Unavailable(val errorCode: InferenceAuditFailureCode?) : InferenceActivityDetailResult
}

internal data class InferenceAuditStartupState(val interruptedRecords: Int = 0, val errorCode: InferenceAuditFailureCode? = null)

/** Read-only presentation adapter plus explicit lifecycle recovery operations for the local audit ledger. */
internal class HarnessInferenceActivitySource(private val repository: InferenceAuditRepository) {
    fun snapshot(
        filter: InferenceActivityFilter = InferenceActivityFilter(),
        nowEpochMs: Long = System.currentTimeMillis(),
        limit: Int = DEFAULT_ACTIVITY_LIMIT,
    ): InferenceActivityUiState {
        require(nowEpochMs >= 0) { "Activity snapshot timestamp must not be negative" }
        val metadata = when (val result = repository.recent(InferenceAuditQuery(limit = MAX_AUDIT_QUERY_LIMIT))) {
            is InferenceAuditResult.Success -> result.value
            is InferenceAuditResult.Failure -> return InferenceActivityUiState(errorCode = result.code)
        }
        val options = filterOptions(metadata, filter.applicationId)
        val query = InferenceAuditQuery(
            limit = limit,
            applicationId = filter.applicationId?.let(::ApplicationId),
            useCaseId = filter.useCaseId?.let(::UseCaseId),
            statuses = filter.status?.let(::setOf).orEmpty(),
            afterReceivedAtEpochMs = filter.period.lowerBound(nowEpochMs),
        )
        return when (val result = repository.recent(query)) {
            is InferenceAuditResult.Success -> InferenceActivityUiState(
                items = result.value.map(::listItem),
                filterOptions = options,
                hasTerminalHistory = metadata.any { it.status.isTerminal },
            )

            is InferenceAuditResult.Failure -> InferenceActivityUiState(
                filterOptions = options,
                hasTerminalHistory = metadata.any { it.status.isTerminal },
                errorCode = result.code,
            )
        }
    }

    fun detail(requestId: String): InferenceActivityDetailResult {
        val id = runCatching { RequestId(requestId) }.getOrNull() ?: return InferenceActivityDetailResult.Unavailable(null)
        return when (val result = repository.find(id)) {
            is InferenceAuditResult.Success -> {
                val record = result.value ?: return InferenceActivityDetailResult.Unavailable(null)
                InferenceActivityDetailResult.Available(detail(record))
            }

            is InferenceAuditResult.Failure -> InferenceActivityDetailResult.Unavailable(result.code)
        }
    }

    fun clearTerminalHistory(): InferenceAuditResult<Int> = repository.clearTerminalHistory()

    fun reconcileInterrupted(nowEpochMs: Long): InferenceAuditStartupState {
        require(nowEpochMs >= 0) { "Reconciliation timestamp must not be negative" }
        val nonTerminal = when (val result = repository.nonTerminal(MAX_RECONCILIATION_RECORDS)) {
            is InferenceAuditResult.Success -> result.value
            is InferenceAuditResult.Failure -> return InferenceAuditStartupState(errorCode = result.code)
        }
        var interrupted = 0
        nonTerminal.forEach { record ->
            val terminal = InferenceAuditTerminal(
                requestId = record.requestId,
                status = InferenceAuditStatus.INTERRUPTED,
                completedAtEpochMs = maxOf(nowEpochMs, record.admission.receivedAtEpochMs),
                terminalCode = InferenceAuditTerminalCode(HOST_PROCESS_LOSS_CODE),
            )
            when (val result = repository.recordTerminal(terminal)) {
                is InferenceAuditResult.Success -> interrupted += 1
                is InferenceAuditResult.Failure -> return InferenceAuditStartupState(interrupted, result.code)
            }
        }
        return InferenceAuditStartupState(interruptedRecords = interrupted)
    }

    private fun filterOptions(
        summaries: List<InferenceAuditSummary>,
        selectedApplicationId: String?,
    ): InferenceActivityFilterOptions {
        val applications = summaries
            .distinctBy { it.origin.applicationId.value }
            .map { summary ->
                InferenceActivityFilterOption(
                    id = summary.origin.applicationId.value,
                    label = applicationLabel(summary.origin.applicationId.value),
                )
            }.sortedBy(InferenceActivityFilterOption::label)
        val useCases = summaries.asSequence()
            .filter { selectedApplicationId == null || it.origin.applicationId.value == selectedApplicationId }
            .distinctBy { it.origin.useCaseId.value }
            .map { summary ->
                InferenceActivityFilterOption(
                    id = summary.origin.useCaseId.value,
                    label = useCaseLabel(summary.origin.useCaseId.value),
                )
            }.sortedBy(InferenceActivityFilterOption::label)
            .toList()
        return InferenceActivityFilterOptions(applications = applications, useCases = useCases)
    }

    private fun listItem(summary: InferenceAuditSummary): InferenceActivityListItem = InferenceActivityListItem(
        requestId = summary.requestId.value,
        applicationLabel = applicationLabel(summary.origin.applicationId.value),
        applicationId = summary.origin.applicationId.value,
        verifiedPackageName = summary.origin.verifiedPackageName,
        useCaseId = summary.origin.useCaseId.value,
        status = summary.status,
        receivedAtEpochMs = summary.receivedAtEpochMs,
        completedAtEpochMs = summary.completedAtEpochMs,
        modelDigest = summary.modelDigest?.sha256,
        totalMs = summary.totalMs,
        decodeTokensPerSecond = summary.decodeTokensPerSecond,
    )

    private fun detail(record: InferenceAuditRecord): InferenceActivityDetail {
        val execution = record.prepared?.execution
        val terminal = record.terminal
        val metrics = terminal?.metrics
        return InferenceActivityDetail(
            requestId = record.requestId.value,
            applicationLabel = applicationLabel(record.admission.origin.applicationId.value),
            applicationId = record.admission.origin.applicationId.value,
            verifiedPackageName = record.admission.origin.verifiedPackageName,
            useCaseId = record.admission.origin.useCaseId.value,
            status = record.status,
            receivedAtEpochMs = record.admission.receivedAtEpochMs,
            completedAtEpochMs = terminal?.completedAtEpochMs,
            input = record.admission.input.displayText(),
            effectivePrompt = record.prepared?.effectivePrompt,
            answerOutput = terminal?.content?.answerOutput,
            reasoningOutput = terminal?.content?.reasoningOutput,
            modelDigest = execution?.modelDigest?.sha256,
            modelLoadKind = execution?.modelLoadKind?.name,
            presetId = execution?.presetId,
            presetVersion = execution?.presetVersion,
            backendId = execution?.backendId,
            backendRevision = execution?.backendRevision,
            backendExecutionFingerprint = execution?.backendExecutionFingerprint,
            effectivePlacement = execution?.effectivePlacement,
            queueMs = metrics?.queueMs,
            modelLoadMs = metrics?.modelLoadMs,
            timeToFirstTokenMs = metrics?.timeToFirstTokenMs,
            timeToFirstAnswerMs = metrics?.timeToFirstAnswerMs,
            totalMs = metrics?.totalMs,
            inputTokens = metrics?.inputTokens,
            outputTokens = metrics?.outputTokens,
            reasoningTokens = metrics?.reasoningTokens,
            answerTokens = metrics?.answerTokens,
            decodeTokensPerSecond = metrics?.decodeTokensPerSecond,
            prefillMs = metrics?.prefillMs,
            decodeMs = metrics?.decodeMs,
            promptPlanningMs = metrics?.promptPlanningMs,
            contextCreationMs = metrics?.contextCreationMs,
            stopReason = metrics?.stopReason?.name,
            terminalCode = terminal?.terminalCode?.value,
        )
    }

    private fun applicationLabel(applicationId: String): String = when (applicationId) {
        HarnessSharedRuntimeBindings.redactGuardApplicationId.value -> "RedactGuard"
        HarnessSharedRuntimeBindings.consoleApplicationId.value -> "OMBRA Console"
        HarnessRuntimeGraph.APPLICATION_ID.value -> "Harnex"
        else -> humanizeIdentifier(applicationId)
    }

    private fun useCaseLabel(useCaseId: String): String = when (useCaseId) {
        HarnessSharedRuntimeBindings.ombraUseCaseId.value -> "Document PII detection"
        HarnessSharedRuntimeBindings.consoleUseCaseId.value -> "Console inference"
        else -> humanizeIdentifier(useCaseId)
    }

    private companion object {
        const val DEFAULT_ACTIVITY_LIMIT = 100
        const val MAX_RECONCILIATION_RECORDS = 200
        const val HOST_PROCESS_LOSS_CODE = "HOST_PROCESS_LOSS"
    }
}

private fun humanizeIdentifier(value: String): String = value
    .split('-', '_')
    .filter(String::isNotBlank)
    .joinToString(" ") { token -> token.replaceFirstChar(Char::uppercase) }
    .ifBlank { value }

private fun InferenceAuditInput.displayText(): String = when (this) {
    is InferenceAuditInput.Text -> value

    is InferenceAuditInput.RawCompletion -> value

    is InferenceAuditInput.Messages -> values.joinToString(separator = "\n\n") { message ->
        "${message.role.name.lowercase()}: ${message.content}"
    }
}
