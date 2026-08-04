package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.LogLevel
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HarnessDiagnosticsSourceTest {
    @Test
    fun `maps bounded privacy-safe run metrics`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 10, maxLogs = 10)
        val requestId = RequestId("request-123")
        repository.recordRun(
            GenerationRunRecord(
                requestId = requestId,
                applicationId = ApplicationId("app"),
                useCaseId = UseCaseId("manual-inference-playground"),
                modelDigest = ModelDigest("a".repeat(64)),
                startedAtEpochMs = 100,
                completedAtEpochMs = 200,
                status = RunStatus.COMPLETED,
                queueMs = 1,
                modelLoadMs = 12,
                timeToFirstTokenMs = 34,
                totalMs = 100,
                inputTokens = 5,
                outputTokens = 8,
                decodeTokensPerSecond = 12.5,
                errorCode = null,
                modelLoadKind = ModelLoadKind.COLD,
            ),
        )
        repository.appendLog(
            StructuredLog(
                timestampEpochMs = 200,
                level = LogLevel.INFO,
                component = "runtime",
                event = "generation.completed",
                requestId = requestId,
                fields = mapOf("outputTokens" to "8"),
            ),
        )
        val source = HarnessDiagnosticsSource(
            telemetryRepository = repository,
            runtimeSnapshot = {
                RuntimeSnapshot(RuntimeState.READY, ModelDigest("a".repeat(64)), 0, 0)
            },
        )

        val state = source.snapshot()

        assertNull(state.sourceError)
        assertEquals(1, state.runs.size)
        assertEquals("Completed", state.runs.single().status)
        assertEquals("34 ms", state.runs.single().timeToFirstToken)
        assertEquals("12.50 tok/s", state.runs.single().throughput)
        assertEquals(1, state.logs.size)
    }

    @Test
    fun `maps health results and worst aggregate status`() {
        val repository = InMemoryTelemetryRepository()
        repository.saveHealth(HealthCheckResult("runtime.state", HealthStatus.PASS, "Operational", 1))
        repository.saveHealth(HealthCheckResult("model.integrity", HealthStatus.WARN, "Not verified", 2))

        val state = HarnessDiagnosticsSource(
            telemetryRepository = repository,
            runtimeSnapshot = { null },
        ).snapshot()

        assertEquals("Warning", state.healthStatus)
        assertEquals(2, state.health.size)
        assertEquals("2 ms", state.health.single { it.id == "model.integrity" }.duration)
    }

    @Test
    fun `diagnostics state does not expose prompt or generated output fields`() {
        val sensitivePrompt = "PRIVATE_PROMPT_SENTINEL"
        val sensitiveOutput = "PRIVATE_OUTPUT_SENTINEL"
        val repository = InMemoryTelemetryRepository(maxRuns = 10, maxLogs = 10)
        val requestId = RequestId("request-private")
        repository.recordRun(
            GenerationRunRecord(
                requestId = requestId,
                applicationId = ApplicationId("app"),
                useCaseId = UseCaseId("manual-inference-playground"),
                modelDigest = ModelDigest("b".repeat(64)),
                startedAtEpochMs = 100,
                completedAtEpochMs = null,
                status = RunStatus.QUEUED,
                queueMs = null,
                modelLoadMs = null,
                timeToFirstTokenMs = null,
                totalMs = null,
                inputTokens = null,
                outputTokens = null,
                decodeTokensPerSecond = null,
                errorCode = null,
            ),
        )
        repository.appendLog(
            StructuredLog(
                timestampEpochMs = 100,
                level = LogLevel.INFO,
                component = "runtime",
                event = "generation.queued",
                requestId = requestId,
                fields = mapOf("applicationId" to "app", "useCaseId" to "manual-inference-playground"),
            ),
        )

        val state = HarnessDiagnosticsSource(
            telemetryRepository = repository,
            runtimeSnapshot = { null },
        ).snapshot()
        val visibleDiagnostics = buildString {
            append(state.runs.joinToString())
            append(state.logs.joinToString())
            append(state.health.joinToString())
        }

        assertFalse(visibleDiagnostics.contains(sensitivePrompt))
        assertFalse(visibleDiagnostics.contains(sensitiveOutput))
        assertFalse(state.logs.any { "prompt" in it.fields.keys || "output" in it.fields.keys })
    }
}
