package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.ModelIntegrityTarget
import io.github.daniele21.localllm.observability.SanityExecutionResult
import io.github.daniele21.localllm.observability.SanityFixture
import io.github.daniele21.localllm.observability.SanityRule
import io.github.daniele21.localllm.observability.SanitySuiteDefinition
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class HealthEngineTest {
    @Test
    fun `valid model passes every integrity check and persists findings`() {
        val digest = ModelDigest("a".repeat(64))
        val file = modelFile("integrity-pass")
        val stored = StoredModel(digest, file, file.length(), verified = true)
        val telemetry = InMemoryTelemetryRepository()
        val engine = engine(
            modelStore = FakeModelStore(stored, VerificationResult(true, digest, "valid")),
            telemetry = telemetry,
        )

        val report = engine.runModelIntegrity(
            ModelIntegrityTarget(
                id = "assistant-model-v1",
                digest = digest,
                expectedSizeBytes = file.length(),
            ),
        )

        assertEquals(HealthStatus.PASS, report.status)
        assertEquals(5, report.findings.size)
        assertTrue(report.findings.all { it.status == HealthStatus.PASS })
        assertEquals(5, telemetry.healthResults().size)
    }

    @Test
    fun `missing model fails presence and marks dependent checks not run`() {
        val digest = ModelDigest("b".repeat(64))
        val telemetry = InMemoryTelemetryRepository()
        val engine = engine(
            modelStore = FakeModelStore(null, VerificationResult(false, null, "missing")),
            telemetry = telemetry,
        )

        val report = engine.runModelIntegrity(
            ModelIntegrityTarget("missing-model", digest, expectedSizeBytes = 12L),
        )

        assertEquals(HealthStatus.FAIL, report.status)
        assertEquals(HealthStatus.FAIL, report.findings.first().status)
        assertTrue(report.findings.drop(1).all { it.status == HealthStatus.NOT_RUN })
        assertTrue(report.findings.first().remediation?.contains("Import") == true)
    }

    @Test
    fun `sanity suite evaluates deterministic output rules`() {
        val telemetry = InMemoryTelemetryRepository()
        val engine = engine(
            modelStore = FakeModelStore(null, VerificationResult(false, null, "unused")),
            telemetry = telemetry,
            execution = SanityExecutionResult(
                output = "{label=ok}",
                outputTokens = 3,
                durationMs = 7L,
            ),
        )
        val fixture = fixture(
            rules = listOf(
                SanityRule.nonEmpty("non-empty"),
                SanityRule.contains("contains-label", "label"),
                SanityRule.notContains("no-error", "error"),
                SanityRule.matchesRegex("shape", "\\{.*}"),
                SanityRule.maxOutputTokens("token-limit", 4),
            ),
        )

        val report = engine.runSanitySuite(SanitySuiteDefinition("assistant", listOf(fixture)))

        assertEquals(HealthStatus.PASS, report.status)
        assertEquals(6, report.findings.size)
        assertTrue(report.findings.all { it.status == HealthStatus.PASS })
    }

    @Test
    fun `sanity failures do not persist generated content`() {
        val telemetry = InMemoryTelemetryRepository()
        val engine = engine(
            modelStore = FakeModelStore(null, VerificationResult(false, null, "unused")),
            telemetry = telemetry,
            execution = SanityExecutionResult(
                output = "private-generated-content",
                outputTokens = 3,
                durationMs = 5L,
            ),
        )
        val fixture = fixture(
            rules = listOf(SanityRule.exact("version-lock", "expected-output")),
        )

        val report = engine.runSanitySuite(SanitySuiteDefinition("privacy", listOf(fixture)))

        assertEquals(HealthStatus.FAIL, report.status)
        assertFalse(
            telemetry.healthResults().any { result ->
                result.detail.contains("private-generated-content") || result.detail.contains("expected-output")
            },
        )
    }

    @Test
    fun `failed execution marks assertions not run`() {
        val engine = engine(
            modelStore = FakeModelStore(null, VerificationResult(false, null, "unused")),
            telemetry = InMemoryTelemetryRepository(),
            execution = SanityExecutionResult(
                output = null,
                outputTokens = null,
                durationMs = 3L,
                errorCode = "MODEL_UNAVAILABLE",
            ),
        )

        val report = engine.runSanitySuite(
            SanitySuiteDefinition(
                id = "unavailable",
                fixtures = listOf(fixture(listOf(SanityRule.nonEmpty("non-empty")))),
            ),
        )

        assertEquals(HealthStatus.FAIL, report.status)
        assertEquals(HealthStatus.FAIL, report.findings[0].status)
        assertEquals(HealthStatus.NOT_RUN, report.findings[1].status)
    }

    private fun engine(
        modelStore: ModelStore,
        telemetry: InMemoryTelemetryRepository,
        execution: SanityExecutionResult = SanityExecutionResult("ok", 1, 1L),
    ): HealthEngine {
        val nanos = AtomicLong(0L)
        return HealthEngine(
            modelStore = modelStore,
            telemetryRepository = telemetry,
            sanityExecutor = { execution },
            epochClock = { 1_000L },
            monotonicClock = { nanos.addAndGet(1_000_000L) },
        )
    }

    private fun fixture(rules: List<SanityRule>): SanityFixture = SanityFixture(
        id = "fixture-1",
        applicationId = ApplicationId("app"),
        useCaseId = UseCaseId("assistant"),
        input = "Return the deterministic fixture response.",
        rules = rules,
    )

    private fun modelFile(prefix: String): File = File.createTempFile(prefix, ".gguf").apply {
        writeText("GGUF deterministic fixture")
        deleteOnExit()
    }
}

private class FakeModelStore(
    private val storedModel: StoredModel?,
    private val verification: VerificationResult,
) : ModelStore {
    override fun find(digest: ModelDigest): StoredModel? = storedModel?.takeIf { it.digest == digest }

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not required by this test")

    override fun verify(digest: ModelDigest): VerificationResult = verification

    override fun remove(digest: ModelDigest): Boolean = false

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(
        modelCount = if (storedModel == null) 0 else 1,
        totalBytes = storedModel?.sizeBytes ?: 0L,
        entries = listOfNotNull(storedModel),
    )
}
