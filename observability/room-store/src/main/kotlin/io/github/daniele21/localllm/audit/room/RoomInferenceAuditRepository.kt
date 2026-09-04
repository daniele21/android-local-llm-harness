package io.github.daniele21.localllm.audit.room

import android.content.Context
import androidx.room.Room
import io.github.daniele21.localllm.audit.InferenceAuditAdmission
import io.github.daniele21.localllm.audit.InferenceAuditExecutionIdentity
import io.github.daniele21.localllm.audit.InferenceAuditFailureCode
import io.github.daniele21.localllm.audit.InferenceAuditMetrics
import io.github.daniele21.localllm.audit.InferenceAuditOrigin
import io.github.daniele21.localllm.audit.InferenceAuditOriginKind
import io.github.daniele21.localllm.audit.InferenceAuditPrepared
import io.github.daniele21.localllm.audit.InferenceAuditQuery
import io.github.daniele21.localllm.audit.InferenceAuditRecord
import io.github.daniele21.localllm.audit.InferenceAuditRepository
import io.github.daniele21.localllm.audit.InferenceAuditResult
import io.github.daniele21.localllm.audit.InferenceAuditRetentionPolicy
import io.github.daniele21.localllm.audit.InferenceAuditStatus
import io.github.daniele21.localllm.audit.InferenceAuditSummary
import io.github.daniele21.localllm.audit.InferenceAuditTerminal
import io.github.daniele21.localllm.audit.InferenceAuditTerminalCode
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.UseCaseId
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("TooManyFunctions")
class RoomInferenceAuditRepository internal constructor(
    private val dao: InferenceAuditDao,
    private val retention: InferenceAuditRetentionPolicy,
    private val cipher: InferenceAuditCipher,
    private val executor: ExecutorService,
    private val closeDatabase: () -> Unit = {},
) : InferenceAuditRepository {
    private val closed = AtomicBoolean(false)

    override fun admit(admission: InferenceAuditAdmission): InferenceAuditResult<Unit> = executeStrict {
        val existing = dao.find(admission.requestId.value)
        if (existing != null) {
            val current = decodeRecord(existing)
            return@executeStrict if (current.admission == admission) successUnit() else invalidState()
        }
        persist(
            record = InferenceAuditRecord(admission = admission, status = InferenceAuditStatus.ADMITTED),
            existing = null,
            nowEpochMs = admission.receivedAtEpochMs,
        )
    }

    override fun markPrepared(prepared: InferenceAuditPrepared): InferenceAuditResult<Unit> = executeStrict {
        val existing = dao.find(prepared.requestId.value) ?: return@executeStrict notFound()
        val current = decodeRecord(existing)
        if (current.prepared != null) {
            return@executeStrict if (current.prepared == prepared) successUnit() else invalidState()
        }
        if (current.status != InferenceAuditStatus.ADMITTED) return@executeStrict invalidState()
        persist(
            record = current.copy(status = InferenceAuditStatus.PREPARED, prepared = prepared),
            existing = existing,
            nowEpochMs = prepared.preparedAtEpochMs,
        )
    }

    override fun markRunning(requestId: RequestId, runningAtEpochMs: Long): InferenceAuditResult<Unit> = executeStrict {
        if (runningAtEpochMs < 0) return@executeStrict invalidState()
        val existing = dao.find(requestId.value) ?: return@executeStrict notFound()
        val current = decodeRecord(existing)
        if (current.runningAtEpochMs != null) {
            return@executeStrict if (current.runningAtEpochMs == runningAtEpochMs) successUnit() else invalidState()
        }
        if (current.status != InferenceAuditStatus.ADMITTED && current.status != InferenceAuditStatus.PREPARED) {
            return@executeStrict invalidState()
        }
        persist(
            record = current.copy(status = InferenceAuditStatus.RUNNING, runningAtEpochMs = runningAtEpochMs),
            existing = existing,
            nowEpochMs = runningAtEpochMs,
        )
    }

    override fun recordTerminal(terminal: InferenceAuditTerminal): InferenceAuditResult<Unit> = executeStrict {
        val existing = dao.find(terminal.requestId.value) ?: return@executeStrict notFound()
        val current = decodeRecord(existing)
        if (current.terminal != null) {
            return@executeStrict if (current.terminal == terminal) successUnit() else invalidState()
        }
        if (current.status.isTerminal) return@executeStrict invalidState()
        if (terminal.status == InferenceAuditStatus.COMPLETED && current.status != InferenceAuditStatus.RUNNING) {
            return@executeStrict invalidState()
        }
        if (terminal.completedAtEpochMs < current.admission.receivedAtEpochMs) return@executeStrict invalidState()
        persist(
            record = current.copy(status = terminal.status, terminal = terminal),
            existing = existing,
            nowEpochMs = terminal.completedAtEpochMs,
        )
    }

    override fun recent(query: InferenceAuditQuery): InferenceAuditResult<List<InferenceAuditSummary>> = executeStrict {
        val applicationId = query.applicationId?.value
        val useCaseId = query.useCaseId?.value
        val entities = if (query.statuses.isEmpty()) {
            dao.recent(query.limit, applicationId, useCaseId, query.beforeReceivedAtEpochMs)
        } else {
            dao.recentWithStatuses(
                query.limit,
                applicationId,
                useCaseId,
                query.statuses.map(InferenceAuditStatus::name),
                query.beforeReceivedAtEpochMs,
            )
        }
        InferenceAuditResult.Success(entities.map(::summaryFromEntity))
    }

    override fun find(requestId: RequestId): InferenceAuditResult<InferenceAuditRecord?> = executeStrict {
        InferenceAuditResult.Success(dao.find(requestId.value)?.let(::decodeRecord))
    }

    override fun nonTerminal(limit: Int): InferenceAuditResult<List<InferenceAuditRecord>> = executeStrict {
        if (limit !in 1..io.github.daniele21.localllm.audit.MAX_AUDIT_QUERY_LIMIT) {
            return@executeStrict invalidState()
        }
        InferenceAuditResult.Success(dao.nonTerminal(limit).map(::decodeRecord))
    }

    override fun clearTerminalHistory(): InferenceAuditResult<Int> = executeStrict {
        InferenceAuditResult.Success(dao.clearTerminalHistory())
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdown()
        try {
            if (!executor.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (error: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        } finally {
            closeDatabase()
        }
    }

    private fun persist(
        record: InferenceAuditRecord,
        existing: InferenceAuditEntities.InferenceAuditEntity?,
        nowEpochMs: Long,
    ): InferenceAuditResult<Unit> {
        val payload = InferenceAuditSensitivePayload(
            input = record.admission.input,
            effectivePrompt = record.prepared?.effectivePrompt,
            terminalContent = record.terminal?.content,
        )
        val encrypted = cipher.seal(InferenceAuditSensitiveCodec.encode(payload))
        val entity = entityFromRecord(record, encrypted)
        if (!makeCapacityFor(entity, existing, nowEpochMs)) {
            return InferenceAuditResult.Failure(InferenceAuditFailureCode.STORAGE_FAILURE)
        }
        dao.upsert(entity)
        return successUnit()
    }

    private fun makeCapacityFor(
        candidate: InferenceAuditEntities.InferenceAuditEntity,
        existing: InferenceAuditEntities.InferenceAuditEntity?,
        nowEpochMs: Long,
    ): Boolean {
        val ageCutoff = (nowEpochMs - retention.maxAgeMs).coerceAtLeast(0L)
        if (ageCutoff > 0L) dao.deleteTerminalOlderThan(ageCutoff)

        var projectedCount = dao.countRecords() + if (existing == null) 1 else 0
        var projectedBytes =
            dao.encryptedContentBytes() - (existing?.encryptedContentBytes ?: 0L) + candidate.encryptedContentBytes
        if (candidate.encryptedContentBytes > retention.maxEncryptedContentBytes) return false

        while (projectedCount > retention.maxRecords || projectedBytes > retention.maxEncryptedContentBytes) {
            val evictedBytes = evictOldestTerminal(candidate.requestId) ?: return false
            projectedCount -= 1
            projectedBytes -= evictedBytes
        }
        return true
    }

    private fun evictOldestTerminal(protectedRequestId: String): Long? {
        val oldestTerminalId = dao.oldestTerminalRequestIds(1).firstOrNull() ?: return null
        if (oldestTerminalId == protectedRequestId) return null
        val evicted = dao.find(oldestTerminalId) ?: return null
        return if (dao.deleteByRequestId(oldestTerminalId) > 0) evicted.encryptedContentBytes else null
    }

    private fun decodeRecord(entity: InferenceAuditEntities.InferenceAuditEntity): InferenceAuditRecord = try {
        val payload = InferenceAuditSensitiveCodec.decode(cipher.open(entity.encryptedContent))
        val status = InferenceAuditStatus.valueOf(entity.status)
        val admission = InferenceAuditAdmission(
            requestId = RequestId(entity.requestId),
            origin = InferenceAuditOrigin(
                kind = InferenceAuditOriginKind.valueOf(entity.originKind),
                applicationId = ApplicationId(entity.applicationId),
                useCaseId = UseCaseId(entity.useCaseId),
                verifiedPackageName = entity.verifiedPackageName,
            ),
            receivedAtEpochMs = entity.receivedAtEpochMs,
            input = payload.input,
        )
        val prepared = entity.preparedAtEpochMs?.let { preparedAt ->
            InferenceAuditPrepared(
                requestId = admission.requestId,
                preparedAtEpochMs = preparedAt,
                effectivePrompt = payload.effectivePrompt,
                execution = executionIdentity(entity),
            )
        }
        val terminal = if (status.isTerminal) terminal(entity, admission.requestId, status, payload) else null
        InferenceAuditRecord(
            admission = admission,
            status = status,
            prepared = prepared,
            runningAtEpochMs = entity.runningAtEpochMs,
            terminal = terminal,
        )
    } catch (error: InferenceAuditCipherException) {
        throw error
    } catch (error: IllegalArgumentException) {
        throw InferenceAuditCipherException(InferenceAuditFailureCode.CORRUPT_CONTENT, error)
    } catch (error: IllegalStateException) {
        throw InferenceAuditCipherException(InferenceAuditFailureCode.CORRUPT_CONTENT, error)
    } catch (error: IOException) {
        throw InferenceAuditCipherException(InferenceAuditFailureCode.CORRUPT_CONTENT, error)
    }

    private fun executionIdentity(entity: InferenceAuditEntities.InferenceAuditEntity): InferenceAuditExecutionIdentity =
        InferenceAuditExecutionIdentity(
            modelDigest = ModelDigest(requireNotNull(entity.modelDigest) { "Prepared audit row is missing model digest" }),
            modelLoadKind = entity.modelLoadKind?.let(ModelLoadKind::valueOf) ?: ModelLoadKind.UNKNOWN,
            presetId = entity.presetId,
            presetVersion = entity.presetVersion,
            backendId = entity.backendId,
            backendRevision = entity.backendRevision,
            backendExecutionFingerprint = entity.backendExecutionFingerprint,
            effectivePlacement = entity.effectivePlacement,
            useCaseRevision = entity.useCaseRevision,
            bindingRevision = entity.bindingRevision,
        )

    private fun terminal(
        entity: InferenceAuditEntities.InferenceAuditEntity,
        requestId: RequestId,
        status: InferenceAuditStatus,
        payload: InferenceAuditSensitivePayload,
    ): InferenceAuditTerminal = InferenceAuditTerminal(
        requestId = requestId,
        status = status,
        completedAtEpochMs = requireNotNull(entity.completedAtEpochMs) { "Terminal audit row is missing completion time" },
        content = payload.terminalContent,
        metrics = if (entity.terminalHasMetrics) metrics(entity) else null,
        terminalCode = entity.terminalCode?.let(::InferenceAuditTerminalCode),
    )

    private fun metrics(entity: InferenceAuditEntities.InferenceAuditEntity): InferenceAuditMetrics = InferenceAuditMetrics(
        queueMs = entity.queueMs,
        modelLoadMs = entity.modelLoadMs,
        timeToFirstTokenMs = entity.timeToFirstTokenMs,
        totalMs = entity.totalMs,
        inputTokens = entity.inputTokens,
        outputTokens = entity.outputTokens,
        decodeTokensPerSecond = entity.decodeTokensPerSecond,
        prefillMs = entity.prefillMs,
        decodeMs = entity.decodeMs,
        modelLoadKind = entity.metricModelLoadKind?.let(ModelLoadKind::valueOf) ?: ModelLoadKind.UNKNOWN,
        stopReason = entity.stopReason?.let(StopReason::valueOf) ?: StopReason.UNKNOWN,
        promptPlanningMs = entity.promptPlanningMs,
        contextCreationMs = entity.contextCreationMs,
        timeToFirstAnswerMs = entity.timeToFirstAnswerMs,
        reasoningTokens = entity.reasoningTokens,
        answerTokens = entity.answerTokens,
    )

    private fun entityFromRecord(record: InferenceAuditRecord, encrypted: ByteArray): InferenceAuditEntities.InferenceAuditEntity =
        InferenceAuditEntities.InferenceAuditEntity().apply {
            requestId = record.requestId.value
            originKind = record.admission.origin.kind.name
            applicationId = record.admission.origin.applicationId.value
            useCaseId = record.admission.origin.useCaseId.value
            verifiedPackageName = record.admission.origin.verifiedPackageName
            receivedAtEpochMs = record.admission.receivedAtEpochMs
            status = record.status.name
            preparedAtEpochMs = record.prepared?.preparedAtEpochMs
            runningAtEpochMs = record.runningAtEpochMs
            completedAtEpochMs = record.terminal?.completedAtEpochMs
            record.prepared?.execution?.let { execution ->
                modelDigest = execution.modelDigest.sha256
                modelLoadKind = execution.modelLoadKind.name
                presetId = execution.presetId
                presetVersion = execution.presetVersion
                backendId = execution.backendId
                backendRevision = execution.backendRevision
                backendExecutionFingerprint = execution.backendExecutionFingerprint
                effectivePlacement = execution.effectivePlacement
                useCaseRevision = execution.useCaseRevision
                bindingRevision = execution.bindingRevision
            }
            terminalCode = record.terminal?.terminalCode?.value
            terminalHasMetrics = record.terminal?.metrics != null
            record.terminal?.metrics?.let { value ->
                metricModelLoadKind = value.modelLoadKind.name
                queueMs = value.queueMs
                modelLoadMs = value.modelLoadMs
                timeToFirstTokenMs = value.timeToFirstTokenMs
                totalMs = value.totalMs
                inputTokens = value.inputTokens
                outputTokens = value.outputTokens
                decodeTokensPerSecond = value.decodeTokensPerSecond
                prefillMs = value.prefillMs
                decodeMs = value.decodeMs
                stopReason = value.stopReason.name
                promptPlanningMs = value.promptPlanningMs
                contextCreationMs = value.contextCreationMs
                timeToFirstAnswerMs = value.timeToFirstAnswerMs
                reasoningTokens = value.reasoningTokens
                answerTokens = value.answerTokens
            }
            encryptedContent = encrypted
            encryptedContentBytes = encrypted.size.toLong()
        }

    private fun summaryFromEntity(entity: InferenceAuditEntities.InferenceAuditEntity): InferenceAuditSummary = try {
        InferenceAuditSummary(
            requestId = RequestId(entity.requestId),
            origin = InferenceAuditOrigin(
                kind = InferenceAuditOriginKind.valueOf(entity.originKind),
                applicationId = ApplicationId(entity.applicationId),
                useCaseId = UseCaseId(entity.useCaseId),
                verifiedPackageName = entity.verifiedPackageName,
            ),
            status = InferenceAuditStatus.valueOf(entity.status),
            receivedAtEpochMs = entity.receivedAtEpochMs,
            completedAtEpochMs = entity.completedAtEpochMs,
            modelDigest = entity.modelDigest?.let(::ModelDigest),
            totalMs = entity.totalMs,
            decodeTokensPerSecond = entity.decodeTokensPerSecond,
        )
    } catch (error: IllegalArgumentException) {
        throw InferenceAuditCipherException(InferenceAuditFailureCode.CORRUPT_CONTENT, error)
    } catch (error: IllegalStateException) {
        throw InferenceAuditCipherException(InferenceAuditFailureCode.CORRUPT_CONTENT, error)
    }

    private fun <T> executeStrict(block: () -> InferenceAuditResult<T>): InferenceAuditResult<T> {
        if (closed.get()) return InferenceAuditResult.Failure(InferenceAuditFailureCode.CLOSED)
        return try {
            executor.submit<InferenceAuditResult<T>> { block() }.get()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            InferenceAuditResult.Failure(InferenceAuditFailureCode.STORAGE_FAILURE)
        } catch (error: ExecutionException) {
            failureFor(error.cause ?: error)
        } catch (error: RejectedExecutionException) {
            failureFor(error)
        } catch (error: CancellationException) {
            failureFor(error)
        }
    }

    private fun failureFor(error: Throwable): InferenceAuditResult.Failure = when (error) {
        is InferenceAuditCipherException -> InferenceAuditResult.Failure(error.code)
        else -> InferenceAuditResult.Failure(InferenceAuditFailureCode.STORAGE_FAILURE)
    }

    private fun successUnit(): InferenceAuditResult<Unit> = InferenceAuditResult.Success(Unit)

    private fun invalidState(): InferenceAuditResult.Failure = InferenceAuditResult.Failure(InferenceAuditFailureCode.INVALID_STATE)

    private fun notFound(): InferenceAuditResult.Failure = InferenceAuditResult.Failure(InferenceAuditFailureCode.NOT_FOUND)

    companion object {
        const val DEFAULT_DATABASE_NAME: String = "harnex-inference-audit.db"
        private const val CLOSE_TIMEOUT_SECONDS = 5L

        fun open(
            context: Context,
            databaseName: String = DEFAULT_DATABASE_NAME,
            retention: InferenceAuditRetentionPolicy = InferenceAuditRetentionPolicy(),
        ): RoomInferenceAuditRepository {
            require(databaseName.isNotBlank()) { "Audit database name must not be blank" }
            val database = Room.databaseBuilder(
                context.applicationContext,
                InferenceAuditDatabase::class.java,
                databaseName,
            ).build()
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "harnex-inference-audit-store").apply { isDaemon = true }
            }
            return RoomInferenceAuditRepository(
                dao = database.inferenceAuditDao(),
                retention = retention,
                cipher = AndroidKeystoreInferenceAuditCipher(),
                executor = executor,
                closeDatabase = database::close,
            )
        }
    }
}
