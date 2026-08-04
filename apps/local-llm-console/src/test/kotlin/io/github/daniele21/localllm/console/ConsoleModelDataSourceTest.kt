package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleModelDataSourceTest {
    @Test
    fun `loads model management capabilities independently`() {
        val control = object : ConsoleModelControl {
            override fun snapshot(): ConsoleModelControlState = ConsoleModelControlState(
                available = true,
                source = "Embedded runtime",
                importAvailable = true,
                verifyAvailable = true,
                removeAvailable = true,
            )

            override fun importModel(request: ConsoleModelImportRequest): ConsoleModelOperationOutcome = error("Not used")

            override fun verify(digest: ModelDigest): ConsoleModelOperationOutcome = error("Not used")

            override fun remove(digest: ModelDigest): ConsoleModelOperationOutcome = error("Not used")
        }

        val snapshot = TelemetryConsoleDataSource(
            telemetryRepository = InMemoryTelemetryRepository(),
            modelControl = control,
        ).load()

        assertTrue(snapshot.modelControl.available)
        assertEquals("Embedded runtime", snapshot.modelControl.source)
        assertTrue(snapshot.modelControl.importAvailable)
    }

    @Test
    fun `maps model management discovery failures to a fixed error`() {
        val failingControl = object : ConsoleModelControl {
            override fun snapshot(): ConsoleModelControlState = error("/private/model/store")

            override fun importModel(request: ConsoleModelImportRequest): ConsoleModelOperationOutcome = error("Not used")

            override fun verify(digest: ModelDigest): ConsoleModelOperationOutcome = error("Not used")

            override fun remove(digest: ModelDigest): ConsoleModelOperationOutcome = error("Not used")
        }

        val snapshot = TelemetryConsoleDataSource(
            telemetryRepository = InMemoryTelemetryRepository(),
            modelControl = failingControl,
        ).load()

        assertFalse(snapshot.modelControl.available)
        assertEquals("Model management unavailable", snapshot.modelControl.sourceError)
        assertFalse(snapshot.modelControl.toString().contains("/private/model/store"))
    }
}
