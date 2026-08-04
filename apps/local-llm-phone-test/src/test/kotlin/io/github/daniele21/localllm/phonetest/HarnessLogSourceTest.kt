package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.LogLevel
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessLogSourceTest {
    @Test
    fun `filters logs by level component event request and safe search`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 10, maxLogs = 20)
        val firstRequest = RequestId("request-alpha")
        val secondRequest = RequestId("request-beta")
        repository.appendLog(
            log(
                timestamp = 100,
                level = LogLevel.INFO,
                event = "generation.started",
                requestId = firstRequest,
            ),
        )
        repository.appendLog(
            log(
                timestamp = 200,
                level = LogLevel.ERROR,
                event = "generation.failed",
                requestId = secondRequest,
                fields = mapOf("errorCode" to "BACKEND_FAILURE"),
            ),
        )
        val filter = DiagnosticsLogFilter(
            level = LogLevel.ERROR,
            componentQuery = "runtime",
            eventQuery = "failed",
            requestQuery = "beta",
            searchQuery = "BACKEND_FAILURE",
        )

        val state = HarnessLogSource(repository).snapshot(filter)

        assertNull(state.sourceError)
        assertEquals(2, state.totalCount)
        assertTrue(state.filterActive)
        assertEquals(1, state.logs.size)
        assertEquals("generation.failed", state.logs.single().event)
    }

    @Test
    fun `request timeline is chronological and uses run start as offset origin`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 10, maxLogs = 20)
        val requestId = RequestId("request-timeline")
        repository.recordRun(
            GenerationRunRecord(
                requestId = requestId,
                applicationId = ApplicationId("app"),
                useCaseId = UseCaseId("playground"),
                modelDigest = ModelDigest("a".repeat(64)),
                startedAtEpochMs = 100,
                completedAtEpochMs = 300,
                status = RunStatus.COMPLETED,
                queueMs = 20,
                modelLoadMs = null,
                timeToFirstTokenMs = 50,
                totalMs = 200,
                inputTokens = 5,
                outputTokens = 8,
                decodeTokensPerSecond = 12.0,
                errorCode = null,
                modelLoadKind = ModelLoadKind.WARM,
            ),
        )
        repository.appendLog(log(300, LogLevel.INFO, "generation.completed", requestId))
        repository.appendLog(log(100, LogLevel.INFO, "generation.queued", requestId))
        repository.appendLog(log(150, LogLevel.INFO, "generation.started", requestId))

        val timeline = HarnessLogSource(repository).requestTimeline(requestId.value)

        assertNull(timeline.sourceError)
        assertEquals("COMPLETED", timeline.runStatus)
        assertEquals(
            listOf("generation.queued", "generation.started", "generation.completed"),
            timeline.events.map { it.event },
        )
        assertEquals(listOf(0L, 50L, 200L), timeline.events.map { it.offsetMs })
    }

    @Test
    fun `mapping redacts unsupported fields and shortens model digest`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 10, maxLogs = 20)
        val prompt = "PRIVATE_PROMPT_SENTINEL"
        val output = "PRIVATE_OUTPUT_SENTINEL"
        repository.appendLog(
            log(
                timestamp = 100,
                level = LogLevel.INFO,
                event = "generation.queued",
                requestId = RequestId("request-private"),
                fields = mapOf(
                    "modelDigest" to "b".repeat(64),
                    "prompt" to prompt,
                    "output" to output,
                    "exceptionMessage" to "/private/path/model.gguf",
                ),
            ),
        )

        val state = HarnessLogSource(repository).snapshot()
        val visible = state.logs.single().fields.joinToString()

        assertTrue(visible.contains("b".repeat(12)))
        assertFalse(visible.contains("b".repeat(64)))
        assertFalse(visible.contains(prompt))
        assertFalse(visible.contains(output))
        assertFalse(visible.contains("/private/path"))
    }

    private fun log(
        timestamp: Long,
        level: LogLevel,
        event: String,
        requestId: RequestId,
        fields: Map<String, String> = emptyMap(),
    ): StructuredLog = StructuredLog(
        timestampEpochMs = timestamp,
        level = level,
        component = "runtime",
        event = event,
        requestId = requestId,
        fields = fields,
    )
}
