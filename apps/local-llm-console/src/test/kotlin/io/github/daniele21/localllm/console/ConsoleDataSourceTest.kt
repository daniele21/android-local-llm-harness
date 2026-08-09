package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.BenchmarkExecutionIdentity
import io.github.daniele21.localllm.observability.BenchmarkKey
import io.github.daniele21.localllm.observability.CacheHealthSnapshot
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.LogLevel
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.ThermalStatus
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ConsoleDataSourceTest {
    @Test
    fun `loads bounded telemetry runtime model and cache state`() {
        val repository = InMemoryTelemetryRepository()
        repeat(3) { index -> repository.recordRun(run(index)) }
        repeat(4) { index -> repository.appendLog(log(index)) }
        repository.saveHealth(HealthCheckResult("model-integrity", HealthStatus.PASS, "verified", 8))
        repository.recordResourceSnapshot(resource())
        repository.saveBenchmarkBaseline(baseline())

        val dataSource = TelemetryConsoleDataSource(
            telemetryRepository = repository,
            runtimeStateProvider = ConsoleRuntimeStateProvider {
                ConsoleRuntimeState(
                    status = "Ready",
                    backend = "llama.cpp",
                    loadedModel = "abc123",
                    activeSessions = 1,
                    queueDepth = 2,
                    source = "In process",
                )
            },
            modelInventoryProvider = ConsoleModelInventoryProvider { inventory() },
            cacheControl = object : ConsoleCacheControl {
                override fun snapshot(): ConsoleCacheControlState = ConsoleCacheControlState(
                    available = true,
                    source = "In process",
                    caches = listOf(
                        ConsoleCacheDescriptor(
                            id = "model-integrity",
                            snapshot = CacheHealthSnapshot(1, 0, 0),
                            repairAvailable = true,
                        ),
                    ),
                )

                override fun repair(cacheId: String): ConsoleCacheRepairOutcome = error("Not used")
            },
            clockEpochMs = { 99L },
            runLimit = 2,
            logLimit = 3,
            resourceLimit = 1,
        )

        val snapshot = dataSource.load()

        assertEquals(99L, snapshot.capturedAtEpochMs)
        assertEquals("Ready", snapshot.runtime.status)
        assertEquals(1, snapshot.modelInventory.modelCount)
        assertEquals(1, snapshot.cacheControl.caches.size)
        assertEquals("model-integrity", snapshot.cacheControl.caches.single().id)
        assertEquals(2, snapshot.runs.size)
        assertEquals(3, snapshot.logs.size)
        assertEquals(1, snapshot.health.size)
        assertEquals(1, snapshot.resources.size)
        assertEquals(1, snapshot.benchmarkBaselines.size)
        assertNull(snapshot.sourceError)
    }

    @Test
    fun `maps model store snapshot without exposing private file paths`() {
        val digest = ModelDigest("d".repeat(64))
        val provider = ModelStoreInventoryProvider(
            modelStore = SnapshotModelStore(
                ModelStoreSnapshot(
                    modelCount = 1,
                    totalBytes = 1_024,
                    entries = listOf(
                        StoredModel(
                            digest = digest,
                            file = File("/private/runtime/models/model.gguf"),
                            sizeBytes = 1_024,
                            verified = false,
                        ),
                    ),
                ),
            ),
            source = "Embedded runtime",
        )

        val inventory = provider.snapshot()

        assertEquals(1, inventory.modelCount)
        assertEquals(digest, inventory.entries.single().digest)
        assertEquals(ConsoleModelIntegrity.NOT_CHECKED, inventory.entries.single().integrity)
        assertFalse(inventory.toString().contains("/private/runtime"))
    }

    @Test
    fun `loads request run and chronological correlated timeline`() {
        val repository = InMemoryTelemetryRepository()
        val requestId = RequestId("request-1")
        repository.recordRun(run(1))
        repository.appendLog(log(30, requestId))
        repository.appendLog(log(10, requestId))
        repository.appendLog(log(20, RequestId("other-request")))

        val detail = TelemetryConsoleDataSource(repository).loadRequest(requestId)

        assertEquals(requestId, detail.run?.requestId)
        assertEquals(listOf(10L, 30L), detail.timeline.map { it.timestampEpochMs })
        assertNull(detail.sourceError)
    }

    @Test
    fun `returns privacy safe errors without exposing source failures`() {
        val failingCacheControl = object : ConsoleCacheControl {
            override fun snapshot(): ConsoleCacheControlState = error("private cache path")

            override fun repair(cacheId: String): ConsoleCacheRepairOutcome = error("Not used")
        }
        val dataSource = TelemetryConsoleDataSource(
            telemetryRepository = FailingTelemetryRepository(),
            modelInventoryProvider = ConsoleModelInventoryProvider { error("private model path") },
            cacheControl = failingCacheControl,
            clockEpochMs = { 7L },
        )

        val snapshot = dataSource.load()
        val detail = dataSource.loadRequest(RequestId("request"))

        assertEquals("Telemetry source unavailable", snapshot.sourceError)
        assertEquals(emptyList<GenerationRunRecord>(), snapshot.runs)
        assertEquals("Not connected", snapshot.runtime.status)
        assertEquals("Model inventory unavailable", snapshot.modelInventory.sourceError)
        assertFalse(snapshot.modelInventory.toString().contains("private model path"))
        assertEquals("Cache health unavailable", snapshot.cacheControl.sourceError)
        assertFalse(snapshot.cacheControl.toString().contains("private cache path"))
        assertEquals("Telemetry source unavailable", detail.sourceError)
        assertNull(detail.run)
        assertEquals(emptyList<StructuredLog>(), detail.timeline)
    }

    private fun inventory() = ConsoleModelInventory(
        available = true,
        modelCount = 1,
        totalBytes = 2_048,
        entries = listOf(
            ConsoleInstalledModel(
                digest = ModelDigest("c".repeat(64)),
                sizeBytes = 2_048,
                integrity = ConsoleModelIntegrity.VERIFIED,
            ),
        ),
        source = "In process",
    )

    private fun run(index: Int) = GenerationRunRecord(
        requestId = RequestId("request-$index"),
        applicationId = ApplicationId("app"),
        useCaseId = UseCaseId("chat"),
        modelDigest = ModelDigest("a".repeat(64)),
        startedAtEpochMs = index.toLong(),
        completedAtEpochMs = index.toLong() + 1,
        status = RunStatus.COMPLETED,
        queueMs = 1,
        modelLoadMs = null,
        timeToFirstTokenMs = 2,
        totalMs = 3,
        inputTokens = 4,
        outputTokens = 5,
        decodeTokensPerSecond = 6.0,
        modelLoadKind = ModelLoadKind.WARM,
        errorCode = null,
    )

    private fun log(index: Int, requestId: RequestId? = null) = StructuredLog(
        timestampEpochMs = index.toLong(),
        level = LogLevel.INFO,
        component = "runtime",
        event = "event-$index",
        requestId = requestId,
    )

    private fun resource() = ResourceSnapshot(
        timestampEpochMs = 1,
        processPssBytes = 10,
        nativeHeapBytes = 20,
        javaHeapUsedBytes = 30,
        availableMemoryBytes = 40,
        lowMemory = false,
        thermalStatus = ThermalStatus.NONE,
    )

    private fun baseline() = BenchmarkBaseline(
        key = BenchmarkKey(
            applicationId = ApplicationId("app"),
            useCaseId = UseCaseId("chat"),
            modelDigest = ModelDigest("b".repeat(64)),
            modelLoadKind = ModelLoadKind.COLD,
            executionIdentity = BenchmarkExecutionIdentity.fromFingerprint("e".repeat(64)),
        ),
        capturedAtEpochMs = 1,
        sampleCount = 3,
        medianTimeToFirstTokenMs = 10.0,
        p95TimeToFirstTokenMs = 12.0,
        medianTotalMs = 20.0,
        p95TotalMs = 22.0,
        medianDecodeTokensPerSecond = 8.0,
    )

    private class FailingTelemetryRepository : TelemetryRepository by InMemoryTelemetryRepository() {
        override fun recentRuns(limit: Int): List<GenerationRunRecord> = error("private database path")

        override fun findRun(requestId: RequestId): GenerationRunRecord? = error("private database path")
    }

    private class SnapshotModelStore(private val value: ModelStoreSnapshot) : ModelStore {
        override fun find(digest: ModelDigest): StoredModel? = null

        override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

        override fun verify(digest: ModelDigest): VerificationResult = error("Not used")

        override fun remove(digest: ModelDigest): Boolean = false

        override fun snapshot(): ModelStoreSnapshot = value
    }
}
