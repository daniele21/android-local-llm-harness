package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.observability.HealthFinding
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.ModelIntegrityTarget
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.StoredModel

internal class ModelIntegrityChecker(private val modelStore: ModelStore, private val monotonicClock: () -> Long) {
    fun run(target: ModelIntegrityTarget): List<HealthFinding> {
        val presence = timed("model.present") {
            modelStore.find(target.digest)?.let { model ->
                CheckOutcome(
                    status = HealthStatus.PASS,
                    detail = "The expected model digest is registered in the model store.",
                    value = model,
                )
            } ?: CheckOutcome(
                status = HealthStatus.FAIL,
                detail = "The expected model digest is not registered in the model store.",
                remediation = "Import the exact GGUF artifact before preparing this model profile.",
                value = null,
            )
        }
        val model = presence.value
        if (model == null) {
            return listOf(presence.finding) + dependentChecksNotRun()
        }
        return listOf(
            presence.finding,
            checkFile(model),
            checkSize(model, target.expectedSizeBytes),
            checkDigest(target),
            checkSnapshot(model),
        )
    }

    private fun checkFile(model: StoredModel): HealthFinding = timed("model.file") {
        if (model.file.isFile) {
            CheckOutcome(
                status = HealthStatus.PASS,
                detail = "The content-addressed GGUF file exists and is a regular file.",
                value = Unit,
            )
        } else {
            CheckOutcome(
                status = HealthStatus.FAIL,
                detail = "The content-addressed model path is missing or is not a regular file.",
                remediation = "Remove the stale store entry and import the expected GGUF again.",
                value = Unit,
            )
        }
    }.finding

    private fun checkSize(model: StoredModel, expectedSizeBytes: Long): HealthFinding = timed("model.size") {
        val actualSize = model.file.takeIf { it.isFile }?.length()
        if (actualSize == expectedSizeBytes && model.sizeBytes == expectedSizeBytes) {
            CheckOutcome(
                status = HealthStatus.PASS,
                detail = "The stored and on-disk model sizes match the declared artifact size.",
                value = Unit,
            )
        } else {
            CheckOutcome(
                status = HealthStatus.FAIL,
                detail = "The stored or on-disk model size differs from the declared artifact size.",
                remediation = "Quarantine the artifact and import a file matching the declared digest and size.",
                value = Unit,
            )
        }
    }.finding

    private fun checkDigest(target: ModelIntegrityTarget): HealthFinding = timed("model.digest") {
        val verification = modelStore.verify(target.digest)
        if (verification.valid && verification.actualDigest == target.digest) {
            CheckOutcome(
                status = HealthStatus.PASS,
                detail = "Streaming SHA-256 verification matches the expected model digest.",
                value = Unit,
            )
        } else {
            CheckOutcome(
                status = HealthStatus.FAIL,
                detail = "Streaming SHA-256 verification does not match the expected model digest.",
                remediation = "Remove or quarantine the corrupted artifact and reimport the exact GGUF.",
                value = Unit,
            )
        }
    }.finding

    private fun checkSnapshot(model: StoredModel): HealthFinding = timed("model.snapshot") {
        val snapshotEntry = modelStore.snapshot().entries.firstOrNull { it.digest == model.digest }
        if (snapshotEntry != null && snapshotEntry.file == model.file && snapshotEntry.sizeBytes == model.sizeBytes) {
            CheckOutcome(
                status = HealthStatus.PASS,
                detail = "The model-store snapshot is consistent with the resolved artifact entry.",
                value = Unit,
            )
        } else {
            CheckOutcome(
                status = HealthStatus.WARN,
                detail = "The model-store snapshot is inconsistent with the resolved artifact entry.",
                remediation = "Rebuild store metadata and verify no stale or duplicate entry remains.",
                value = Unit,
            )
        }
    }.finding

    private fun dependentChecksNotRun(): List<HealthFinding> = listOf(
        notRun("model.file", "File checks require a registered model-store entry."),
        notRun("model.size", "Size validation requires a registered model-store entry."),
        notRun("model.digest", "Digest verification requires a registered model-store entry."),
        notRun("model.snapshot", "Snapshot consistency requires a registered model-store entry."),
    )

    private fun notRun(id: String, detail: String): HealthFinding = HealthFinding(
        id = id,
        status = HealthStatus.NOT_RUN,
        detail = detail,
        durationMs = 0L,
        remediation = "Restore or import the expected content-addressed model artifact.",
    )

    private fun <T> timed(id: String, block: () -> CheckOutcome<T>): TimedCheck<T> {
        val startedAt = monotonicClock()
        val outcome = runCatching(block).getOrElse { error ->
            CheckOutcome(
                status = HealthStatus.FAIL,
                detail = "The health check failed unexpectedly.",
                remediation = "Inspect diagnostic logs for ${error::class.java.simpleName} and retry the check.",
                value = null,
            )
        }
        return TimedCheck(
            finding = HealthFinding(
                id = id,
                status = outcome.status,
                detail = outcome.detail,
                durationMs = elapsedMillis(startedAt),
                remediation = outcome.remediation,
            ),
            value = outcome.value,
        )
    }

    private fun elapsedMillis(startedAtNanos: Long): Long = (monotonicClock() - startedAtNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND

    private data class CheckOutcome<T>(val status: HealthStatus, val detail: String, val remediation: String? = null, val value: T?)

    private data class TimedCheck<T>(val finding: HealthFinding, val value: T?)

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
