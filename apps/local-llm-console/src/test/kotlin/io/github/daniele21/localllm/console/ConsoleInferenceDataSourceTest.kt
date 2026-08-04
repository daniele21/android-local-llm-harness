package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ConsoleInferenceDataSourceTest {
    @Test
    fun `loads connected inference state independently`() {
        val target = ConsoleInferenceTarget(ApplicationId("app"), UseCaseId("chat"))
        val control = FixedInferenceControl(
            ConsoleInferenceState(
                available = true,
                source = "Embedded runtime",
                targets = listOf(target),
                phase = ConsoleInferencePhase.IDLE,
            ),
        )
        val snapshot = TelemetryConsoleDataSource(
            telemetryRepository = InMemoryTelemetryRepository(),
            inferenceControl = control,
            clockEpochMs = { 9L },
        ).load()

        assertEquals(9L, snapshot.capturedAtEpochMs)
        assertEquals(ConsoleInferencePhase.IDLE, snapshot.inference.phase)
        assertEquals(target, snapshot.inference.targets.single())
        assertNull(snapshot.sourceError)
    }

    @Test
    fun `isolates inference source failure with fixed privacy safe error`() {
        val failing = object : ConsoleInferenceControl {
            override fun snapshot(): ConsoleInferenceState = error("private runtime path")

            override fun start(request: ConsoleInferenceRequest, listener: ConsoleInferenceListener): ConsoleInferenceOperationOutcome =
                error("Not used")

            override fun cancel(): ConsoleInferenceOperationOutcome = error("Not used")

            override fun clear(): ConsoleInferenceOperationOutcome = error("Not used")

            override fun close() = Unit
        }
        val snapshot = TelemetryConsoleDataSource(
            telemetryRepository = InMemoryTelemetryRepository(),
            inferenceControl = failing,
        ).load()

        assertEquals("Inference playground unavailable", snapshot.inference.sourceError)
        assertEquals(ConsoleInferencePhase.DISCONNECTED, snapshot.inference.phase)
        assertFalse(snapshot.inference.toString().contains("private runtime path"))
        assertNull(snapshot.sourceError)
    }

    private class FixedInferenceControl(private val state: ConsoleInferenceState) : ConsoleInferenceControl {
        override fun snapshot(): ConsoleInferenceState = state

        override fun start(request: ConsoleInferenceRequest, listener: ConsoleInferenceListener): ConsoleInferenceOperationOutcome =
            error("Not used")

        override fun cancel(): ConsoleInferenceOperationOutcome = error("Not used")

        override fun clear(): ConsoleInferenceOperationOutcome = error("Not used")

        override fun close() = Unit
    }
}
