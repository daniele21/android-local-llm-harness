package io.github.daniele21.localllm.observability.room

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.LogLevel
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRetentionPolicy
import io.github.daniele21.localllm.observability.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.Executors

class RoomTelemetryRepositoryTest {
    @Test
    fun `repository applies retention and query semantics`() {
        val dao = FakeTelemetryDao()
        val repository = RoomTelemetryRepository(
            dao = dao,
            retention = TelemetryRetentionPolicy(maxRuns = 2, maxLogs = 3, maxResourceSnapshots = 2),
            executor = Executors.newSingleThreadExecutor(),
        )

        repository.use {
            repository.recordRun(run("r1", 1L))
            repository.recordRun(run("r2", 2L))
            repository.recordRun(run("r3", 3L))

            assertEquals(listOf("r3", "r2"), repository.recentRuns().map { it.requestId.value })
            assertNull(repository.findRun(RequestId("r1")))
            assertEquals(ModelLoadKind.COLD, repository.findRun(RequestId("r3"))?.modelLoadKind)

            repository.appendLog(log(1L, "r2", "queued"))
            repository.appendLog(log(2L, "r3", "started"))
            repository.appendLog(log(3L, "r3", "completed"))
            repository.appendLog(log(4L, null, "runtime.ready"))

            assertEquals(3, repository.recentLogs().size)
            assertEquals(
                listOf("completed", "started"),
                repository.recentLogs(requestId = RequestId("r3")).map { it.event },
            )

            repository.saveHealth(HealthCheckResult("database", HealthStatus.PASS, "ready", 4L))
            assertEquals(listOf("database"), repository.healthResults().map { it.id })

            repository.recordResourceSnapshot(resource(1L))
            repository.recordResourceSnapshot(resource(2L))
            repository.recordResourceSnapshot(resource(3L))
            assertEquals(listOf(3L, 2L), repository.recentResourceSnapshots().map { it.timestampEpochMs })
        }
    }

    @Test
    fun `structured fields encoding is deterministic and lossless`() {
        val fields = linkedMapOf(
            "z-key" to "line one\nline two",
            "a-key" to "value=with=separators",
            "unicode" to "temperatura 42 °C",
        )

        val encoded = TelemetryEntityMapper.encodeFields(fields)
        val reordered = TelemetryEntityMapper.encodeFields(fields.toList().reversed().toMap())

        assertEquals(encoded, reordered)
        assertEquals(fields, TelemetryEntityMapper.decodeFields(encoded))
    }

    private fun run(requestId: String, timestamp: Long): GenerationRunRecord = GenerationRunRecord(
        requestId = RequestId(requestId),
        applicationId = ApplicationId("app"),
        useCaseId = UseCaseId("assistant"),
        modelDigest = ModelDigest("a".repeat(64)),
        startedAtEpochMs = timestamp,
        completedAtEpochMs = timestamp + 1,
        status = RunStatus.COMPLETED,
        queueMs = 1,
        modelLoadMs = 2,
        timeToFirstTokenMs = 3,
        totalMs = 4,
        inputTokens = 5,
        outputTokens = 6,
        decodeTokensPerSecond = 7.0,
        errorCode = null,
        modelLoadKind = ModelLoadKind.COLD,
    )

    private fun log(timestamp: Long, requestId: String?, event: String): StructuredLog = StructuredLog(
        timestampEpochMs = timestamp,
        level = LogLevel.INFO,
        component = "runtime",
        event = event,
        requestId = requestId?.let(::RequestId),
        fields = mapOf("status" to event),
    )

    private fun resource(timestamp: Long): ResourceSnapshot = ResourceSnapshot(
        timestampEpochMs = timestamp,
        processPssBytes = timestamp * 10,
        nativeHeapBytes = timestamp * 20,
        javaHeapUsedBytes = timestamp * 30,
        availableMemoryBytes = timestamp * 40,
        lowMemory = false,
        thermalStatus = ThermalStatus.LIGHT,
    )
}

private class FakeTelemetryDao : TelemetryDao {
    private val runs = linkedMapOf<String, TelemetryEntities.GenerationRunEntity>()
    private val logs = mutableListOf<TelemetryEntities.StructuredLogEntity>()
    private val health = linkedMapOf<String, TelemetryEntities.HealthCheckEntity>()
    private val resources = mutableListOf<TelemetryEntities.ResourceSnapshotEntity>()
    private var nextLogId = 1L
    private var nextResourceId = 1L

    override fun upsertRun(run: TelemetryEntities.GenerationRunEntity) {
        runs[run.requestId] = run
    }

    override fun recentRuns(limit: Int): List<TelemetryEntities.GenerationRunEntity> = runs.values
        .sortedWith(compareByDescending<TelemetryEntities.GenerationRunEntity> { it.startedAtEpochMs }.thenByDescending { it.requestId })
        .take(limit)

    override fun findRun(requestId: String): TelemetryEntities.GenerationRunEntity? = runs[requestId]

    override fun trimRuns(maxRows: Int) {
        val retained = recentRuns(maxRows).mapTo(mutableSetOf()) { it.requestId }
        runs.keys.removeAll { it !in retained }
    }

    override fun insertLog(log: TelemetryEntities.StructuredLogEntity): Long {
        log.id = nextLogId++
        logs += log
        return log.id
    }

    override fun recentLogs(limit: Int): List<TelemetryEntities.StructuredLogEntity> = sortedLogs().take(limit)

    override fun recentLogsForRequest(requestId: String, limit: Int): List<TelemetryEntities.StructuredLogEntity> =
        sortedLogs().filter { it.requestId == requestId }.take(limit)

    override fun trimLogs(maxRows: Int) {
        val retained = recentLogs(maxRows).mapTo(mutableSetOf()) { it.id }
        logs.removeAll { it.id !in retained }
    }

    override fun upsertHealth(result: TelemetryEntities.HealthCheckEntity) {
        health[result.id] = result
    }

    override fun healthResults(): List<TelemetryEntities.HealthCheckEntity> = health.values.sortedBy { it.id }

    override fun insertResourceSnapshot(snapshot: TelemetryEntities.ResourceSnapshotEntity): Long {
        snapshot.id = nextResourceId++
        resources += snapshot
        return snapshot.id
    }

    override fun recentResourceSnapshots(limit: Int): List<TelemetryEntities.ResourceSnapshotEntity> = resources
        .sortedWith(compareByDescending<TelemetryEntities.ResourceSnapshotEntity> { it.timestampEpochMs }.thenByDescending { it.id })
        .take(limit)

    override fun trimResourceSnapshots(maxRows: Int) {
        val retained = recentResourceSnapshots(maxRows).mapTo(mutableSetOf()) { it.id }
        resources.removeAll { it.id !in retained }
    }

    private fun sortedLogs(): List<TelemetryEntities.StructuredLogEntity> = logs.sortedWith(
        compareByDescending<TelemetryEntities.StructuredLogEntity> { it.timestampEpochMs }.thenByDescending { it.id },
    )
}
