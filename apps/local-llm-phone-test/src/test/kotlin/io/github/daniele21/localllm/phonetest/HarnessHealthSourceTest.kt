package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HarnessHealthSourceTest {
    private val digest = ModelDigest("a".repeat(64))
    private val model = ImportedPhoneModel(
        digest = digest,
        fileName = "model.gguf",
        sizeBytes = 1024L,
        architecture = "qwen3",
        quantization = "Q4_K_M",
    )

    @Test
    fun `healthy selected model produces passing suite`() {
        val telemetry = InMemoryTelemetryRepository()
        val source = HarnessHealthSource(
            modelStore = FakeModelStore(valid = true),
            telemetryRepository = telemetry,
            selectedModel = { model },
            runtimeState = { RuntimeState.READY },
        )

        val report = source.runAll()

        assertEquals(HealthStatus.PASS, report.status)
        assertEquals(source.availableChecks().size, report.results.size)
        assertTrue(report.results.all { it.status == HealthStatus.PASS })
        assertEquals(report.results.size, telemetry.healthResults().size)
    }

    @Test
    fun `missing model produces warning without integrity execution`() {
        val source = HarnessHealthSource(
            modelStore = FakeModelStore(valid = true),
            telemetryRepository = InMemoryTelemetryRepository(),
            selectedModel = { null },
            runtimeState = { null },
        )

        val report = source.runAll()

        assertEquals(HealthStatus.WARN, report.status)
        assertEquals(HealthStatus.WARN, report.results.single { it.id == "model.selected" }.status)
        assertEquals(HealthStatus.NOT_RUN, report.results.single { it.id == "model.integrity" }.status)
        assertEquals(HealthStatus.NOT_RUN, report.results.single { it.id == "runtime.state" }.status)
    }

    @Test
    fun `invalid model digest produces failed suite with privacy safe detail`() {
        val report = HarnessHealthSource(
            modelStore = FakeModelStore(valid = false),
            telemetryRepository = InMemoryTelemetryRepository(),
            selectedModel = { model },
            runtimeState = { RuntimeState.READY },
        ).runAll()

        val integrity = report.results.single { it.id == "model.integrity" }
        assertEquals(HealthStatus.FAIL, report.status)
        assertEquals(HealthStatus.FAIL, integrity.status)
        assertTrue(!integrity.detail.contains(model.fileName))
        assertTrue(!integrity.detail.contains(model.digest.sha256))
    }

    private inner class FakeModelStore(private val valid: Boolean) : ModelStore {
        override fun find(digest: ModelDigest): StoredModel? = null

        override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

        override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(
            valid = valid,
            actualDigest = if (valid) digest else null,
            detail = if (valid) "valid" else "invalid",
        )

        override fun remove(digest: ModelDigest): Boolean = false

        override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0L, emptyList())
    }
}
