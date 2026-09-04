package io.github.daniele21.localllm.audit.store

import io.github.daniele21.localllm.audit.InferenceAuditAdmission
import io.github.daniele21.localllm.audit.InferenceAuditInput
import io.github.daniele21.localllm.audit.InferenceAuditPrepared
import io.github.daniele21.localllm.audit.InferenceAuditQuery
import io.github.daniele21.localllm.audit.InferenceAuditRecord
import io.github.daniele21.localllm.audit.InferenceAuditRepository
import io.github.daniele21.localllm.audit.InferenceAuditResult
import io.github.daniele21.localllm.audit.InferenceAuditRetentionPolicy
import io.github.daniele21.localllm.audit.InferenceAuditStatus
import io.github.daniele21.localllm.audit.InferenceAuditSummary
import io.github.daniele21.localllm.audit.InferenceAuditTerminal
import io.github.daniele21.localllm.audit.MAX_AUDIT_QUERY_LIMIT
import io.github.daniele21.localllm.contracts.RequestId
import java.util.concurrent.atomic.AtomicBoolean

/** Deterministic process-local audit store for tests, previews and non-persistent compositions. */
class InMemoryInferenceAuditRepository(private val retention: InferenceAuditRetentionPolicy = InferenceAuditRetentionPolicy()) :
    InferenceAuditRepository {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val records = linkedMapOf<RequestId, InferenceAuditRecord>()

    override fun admit(admission: InferenceAuditAdmission): InferenceAuditResult<Unit> = synchronized(lock) {
        if (closed.get()) return@synchronized closedFailure()
        val existing = records[admission.requestId]
        if (existing != null) {
            return@synchronized if (existing.admission == admission) successUnit() else invalidState()
        }
        records[admission.requestId] = InferenceAuditRecord(
            admission = admission,
            status = InferenceAuditStatus.ADMITTED,
        )
        trimTerminalHistory(admission.receivedAtEpochMs)
        successUnit()
    }

    override fun markPrepared(prepared: InferenceAuditPrepared): InferenceAuditResult<Unit> = synchronized(lock) {
        if (closed.get()) return@synchronized closedFailure()
        val current = records[prepared.requestId] ?: return@synchronized notFound()
        if (current.prepared != null) {
            return@synchronized if (current.prepared == prepared) successUnit() else invalidState()
        }
        if (current.status != InferenceAuditStatus.ADMITTED) return@synchronized invalidState()
        records[prepared.requestId] = current.copy(
            status = InferenceAuditStatus.PREPARED,
            prepared = prepared,
        )
        successUnit()
    }

    override fun markRunning(requestId: RequestId, runningAtEpochMs: Long): InferenceAuditResult<Unit> = synchronized(lock) {
        if (closed.get()) return@synchronized closedFailure()
        if (runningAtEpochMs < 0) return@synchronized invalidState()
        val current = records[requestId] ?: return@synchronized notFound()
        if (current.runningAtEpochMs != null) {
            return@synchronized if (current.runningAtEpochMs == runningAtEpochMs) successUnit() else invalidState()
        }
        if (current.status != InferenceAuditStatus.ADMITTED && current.status != InferenceAuditStatus.PREPARED) {
            return@synchronized invalidState()
        }
        records[requestId] = current.copy(
            status = InferenceAuditStatus.RUNNING,
            runningAtEpochMs = runningAtEpochMs,
        )
        successUnit()
    }

    override fun recordTerminal(terminal: InferenceAuditTerminal): InferenceAuditResult<Unit> = synchronized(lock) {
        if (closed.get()) return@synchronized closedFailure()
        val current = records[terminal.requestId] ?: return@synchronized notFound()
        if (current.terminal != null) {
            return@synchronized if (current.terminal == terminal) successUnit() else invalidState()
        }
        if (current.status.isTerminal) return@synchronized invalidState()
        if (terminal.status == InferenceAuditStatus.COMPLETED && current.status != InferenceAuditStatus.RUNNING) {
            return@synchronized invalidState()
        }
        if (terminal.completedAtEpochMs < current.admission.receivedAtEpochMs) return@synchronized invalidState()
        records[terminal.requestId] = current.copy(
            status = terminal.status,
            terminal = terminal,
        )
        trimTerminalHistory(terminal.completedAtEpochMs)
        successUnit()
    }

    override fun recent(query: InferenceAuditQuery): InferenceAuditResult<List<InferenceAuditSummary>> = synchronized(lock) {
        if (closed.get()) return@synchronized closedFailure()
        val cutoff = query.beforeReceivedAtEpochMs
        val summaries = records.values.asSequence()
            .filter { record -> query.applicationId == null || record.admission.origin.applicationId == query.applicationId }
            .filter { record -> query.useCaseId == null || record.admission.origin.useCaseId == query.useCaseId }
            .filter { record -> query.statuses.isEmpty() || record.status in query.statuses }
            .filter { record -> cutoff == null || record.admission.receivedAtEpochMs < cutoff }
            .sortedByDescending { it.admission.receivedAtEpochMs }
            .take(query.limit)
            .map(::summary)
            .toList()
        InferenceAuditResult.Success(summaries)
    }

    override fun find(requestId: RequestId): InferenceAuditResult<InferenceAuditRecord?> = synchronized(lock) {
        if (closed.get()) return@synchronized closedFailure()
        InferenceAuditResult.Success(records[requestId])
    }

    override fun nonTerminal(limit: Int): InferenceAuditResult<List<InferenceAuditRecord>> = synchronized(lock) {
        if (closed.get()) return@synchronized closedFailure()
        if (limit !in 1..MAX_AUDIT_QUERY_LIMIT) return@synchronized invalidState()
        InferenceAuditResult.Success(
            records.values.asSequence()
                .filterNot { it.status.isTerminal }
                .sortedBy { it.admission.receivedAtEpochMs }
                .take(limit)
                .toList(),
        )
    }

    override fun clearTerminalHistory(): InferenceAuditResult<Int> = synchronized(lock) {
        if (closed.get()) return@synchronized closedFailure()
        val terminalIds = records.values.filter { it.status.isTerminal }.map { it.requestId }
        terminalIds.forEach(records::remove)
        InferenceAuditResult.Success(terminalIds.size)
    }

    override fun close() {
        closed.set(true)
    }

    private fun trimTerminalHistory(nowEpochMs: Long) {
        val ageCutoff = nowEpochMs - retention.maxAgeMs
        terminalRecordsOldestFirst()
            .filter { it.terminal?.completedAtEpochMs?.let { completed -> completed < ageCutoff } == true }
            .forEach { records.remove(it.requestId) }

        while (records.size > retention.maxRecords) {
            val oldestTerminal = terminalRecordsOldestFirst().firstOrNull() ?: break
            records.remove(oldestTerminal.requestId)
        }

        while (sensitiveBytes() > retention.maxEncryptedContentBytes) {
            val oldestTerminal = terminalRecordsOldestFirst().firstOrNull() ?: break
            records.remove(oldestTerminal.requestId)
        }
    }

    private fun terminalRecordsOldestFirst(): List<InferenceAuditRecord> = records.values
        .filter { it.status.isTerminal }
        .sortedBy { it.terminal?.completedAtEpochMs ?: Long.MAX_VALUE }

    private fun sensitiveBytes(): Long = records.values.sumOf { record ->
        inputBytes(record.admission.input) +
            (record.prepared?.effectivePrompt?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L) +
            (record.terminal?.content?.answerOutput?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L) +
            (record.terminal?.content?.reasoningOutput?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L)
    }

    private fun inputBytes(input: InferenceAuditInput): Long = when (input) {
        is InferenceAuditInput.Text -> input.value.toByteArray(Charsets.UTF_8).size.toLong()
        is InferenceAuditInput.RawCompletion -> input.value.toByteArray(Charsets.UTF_8).size.toLong()
        is InferenceAuditInput.Messages -> input.values.sumOf { it.content.toByteArray(Charsets.UTF_8).size.toLong() }
    }

    private fun summary(record: InferenceAuditRecord): InferenceAuditSummary = InferenceAuditSummary(
        requestId = record.requestId,
        origin = record.admission.origin,
        status = record.status,
        receivedAtEpochMs = record.admission.receivedAtEpochMs,
        completedAtEpochMs = record.terminal?.completedAtEpochMs,
        modelDigest = record.prepared?.execution?.modelDigest,
        totalMs = record.terminal?.metrics?.totalMs,
        decodeTokensPerSecond = record.terminal?.metrics?.decodeTokensPerSecond,
    )

    private fun successUnit(): InferenceAuditResult<Unit> = InferenceAuditResult.Success(Unit)

    private fun closedFailure(): InferenceAuditResult.Failure =
        InferenceAuditResult.Failure(io.github.daniele21.localllm.audit.InferenceAuditFailureCode.CLOSED)

    private fun invalidState(): InferenceAuditResult.Failure =
        InferenceAuditResult.Failure(io.github.daniele21.localllm.audit.InferenceAuditFailureCode.INVALID_STATE)

    private fun notFound(): InferenceAuditResult.Failure =
        InferenceAuditResult.Failure(io.github.daniele21.localllm.audit.InferenceAuditFailureCode.NOT_FOUND)
}
