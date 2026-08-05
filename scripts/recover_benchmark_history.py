from pathlib import Path
import subprocess

BASE = "a081d361b9026b09ddb7655c2ee8d58fc449918a"
HEAD = "e48073179c1c56a4d0238b34a4a656d2598ecfd5"
CORE_PATHS = [
    "observability/contracts/src/main/kotlin/io/github/daniele21/localllm/observability/Telemetry.kt",
    "observability/in-memory-store/src/main/kotlin/io/github/daniele21/localllm/observability/store/InMemoryTelemetryRepository.kt",
    "observability/benchmark-engine/src/main/kotlin/io/github/daniele21/localllm/observability/benchmark/BenchmarkEngine.kt",
    "observability/benchmark-engine/src/test/kotlin/io/github/daniele21/localllm/observability/benchmark/BenchmarkEngineTest.kt",
    "observability/room-store/src/main/java/io/github/daniele21/localllm/observability/room/TelemetryDao.java",
    "observability/room-store/src/main/java/io/github/daniele21/localllm/observability/room/TelemetryDatabase.java",
    "observability/room-store/src/main/java/io/github/daniele21/localllm/observability/room/TelemetryEntities.java",
    "observability/room-store/src/main/kotlin/io/github/daniele21/localllm/observability/room/RoomTelemetryRepository.kt",
    "observability/room-store/src/main/kotlin/io/github/daniele21/localllm/observability/room/TelemetryEntityMapper.kt",
    "observability/room-store/src/test/kotlin/io/github/daniele21/localllm/observability/room/RoomTelemetryRepositoryTest.kt",
]


def replace_once(path: str, old: str, new: str, label: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


subprocess.run(["git", "fetch", "origin", BASE, HEAD], check=True)
patch = subprocess.run(
    ["git", "diff", BASE, HEAD, "--", *CORE_PATHS],
    check=True,
    capture_output=True,
).stdout
subprocess.run(["git", "apply", "--3way", "-"], input=patch, check=True)

source_path = "apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/HarnessBenchmarkSource.kt"
replace_once(
    source_path,
    "internal data class BenchmarkReadinessUi(",
    """internal data class BenchmarkHistoryUi(
    val stableId: String,
    val useCase: String,
    val loadKind: String,
    val capturedAt: String,
    val samples: String,
    val medianTtft: String,
    val p95Total: String,
    val medianDecode: String,
    val active: Boolean,
)

internal data class BenchmarkReadinessUi(""",
    "history UI model",
)
replace_once(
    source_path,
    "    val readiness: List<BenchmarkReadinessUi> = emptyList(),",
    "    val readiness: List<BenchmarkReadinessUi> = emptyList(),\n    val history: List<BenchmarkHistoryUi> = emptyList(),",
    "history state",
)
replace_once(
    source_path,
    """            readiness = keys.map(::toReadinessUi),
            eligibleKeys = keys.size,""",
    """            readiness = keys.map(::toReadinessUi),
            history = repository.benchmarkBaselineHistory(HISTORY_LIMIT)
                .filter { model == null || it.key.modelDigest == model.digest }
                .map { baseline -> toHistoryUi(baseline, repository.benchmarkBaselines()) },
            eligibleKeys = keys.size,""",
    "history snapshot",
)
replace_once(
    source_path,
    "\n    private fun toUi(baseline: BenchmarkBaseline): BenchmarkUi",
    """
    private fun toHistoryUi(
        baseline: BenchmarkBaseline,
        activeBaselines: List<BenchmarkBaseline>,
    ): BenchmarkHistoryUi = BenchmarkHistoryUi(
        stableId = "${baseline.key.safeStableId()}:${baseline.capturedAtEpochMs}",
        useCase = baseline.key.useCaseId.value,
        loadKind = baseline.key.modelLoadKind.name,
        capturedAt = java.time.Instant.ofEpochMilli(baseline.capturedAtEpochMs).toString(),
        samples = baseline.sampleCount.toString(),
        medianTtft = baseline.medianTimeToFirstTokenMs.asMilliseconds(),
        p95Total = baseline.p95TotalMs.asMilliseconds(),
        medianDecode = baseline.medianDecodeTokensPerSecond.asThroughput(),
        active = activeBaselines.any { it == baseline },
    )

    private fun toUi(baseline: BenchmarkBaseline): BenchmarkUi""",
    "history mapper",
)
replace_once(
    source_path,
    "        const val RUN_LOOKBACK = 500",
    "        const val RUN_LOOKBACK = 500\n        const val HISTORY_LIMIT = 100",
    "history limit",
)

activity_path = "apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/MainActivity.kt"
replace_once(
    activity_path,
    '                    HarnessMetric("Known keys", benchmarkState.readiness.size.toString(), Modifier.weight(1f))',
    '                    HarnessMetric("Known keys", benchmarkState.readiness.size.toString(), Modifier.weight(1f))\n                    HarnessMetric("History", benchmarkState.history.size.toString(), Modifier.weight(1f))',
    "history summary metric",
)
replace_once(
    activity_path,
    '        items(benchmarkState.baselines, key = { "baseline:${it.stableId}" }) { benchmark ->',
    '''        if (benchmarkState.history.isNotEmpty()) {
            item {
                Text("Retained baseline history", style = MaterialTheme.typography.titleLarge)
            }
        }
        items(benchmarkState.history, key = { "history:${it.stableId}" }) { history ->
            HarnessCard {
                Text(
                    "${history.useCase} · ${history.loadKind}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(history.capturedAt)
                HarnessMetricRow {
                    HarnessMetric("State", if (history.active) "Active" else "Historical", Modifier.weight(1f))
                    HarnessMetric("Samples", history.samples, Modifier.weight(1f))
                }
                HarnessMetricRow {
                    HarnessMetric("Median TTFT", history.medianTtft, Modifier.weight(1f))
                    HarnessMetric("p95 total", history.p95Total, Modifier.weight(1f))
                }
                HarnessMetric("Median decode", history.medianDecode)
            }
        }

        items(benchmarkState.baselines, key = { "baseline:${it.stableId}" }) { benchmark ->''',
    "history cards",
)

phone_test = Path("apps/local-llm-phone-test/src/test/kotlin/io/github/daniele21/localllm/phonetest/HarnessBenchmarkSourceTest.kt")
text = phone_test.read_text(encoding="utf-8")
text = text.replace(
    "import io.github.daniele21.localllm.observability.GenerationRunRecord",
    "import io.github.daniele21.localllm.observability.BenchmarkBaseline\nimport io.github.daniele21.localllm.observability.BenchmarkKey\nimport io.github.daniele21.localllm.observability.GenerationRunRecord",
    1,
)
text = text.replace("import org.junit.Assert.assertEquals", "import org.junit.Assert.assertEquals\nimport org.junit.Assert.assertFalse", 1)
insert = '''
    @Test
    fun `exposes retained history separately from active baselines`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 100, maxLogs = 10)
        val key = BenchmarkKey(
            ApplicationId("play-internal-phone-test"),
            UseCaseId("manual-inference-playground"),
            digest,
            ModelLoadKind.WARM,
        )
        repository.saveBenchmarkBaseline(baseline(key, 10L, 5))
        repository.saveBenchmarkBaseline(baseline(key, 20L, 6))

        val state = HarnessBenchmarkSource(repository) { model }.snapshot()

        assertEquals(1, state.baselines.size)
        assertEquals(2, state.history.size)
        assertTrue(state.history.first().active)
        assertFalse(state.history.last().active)
    }

    private fun baseline(key: BenchmarkKey, capturedAt: Long, samples: Int) = BenchmarkBaseline(
        key = key,
        capturedAtEpochMs = capturedAt,
        sampleCount = samples,
        medianTimeToFirstTokenMs = 20.0,
        p95TimeToFirstTokenMs = 25.0,
        medianTotalMs = 50.0,
        p95TotalMs = 60.0,
        medianDecodeTokensPerSecond = 12.0,
    )
'''
end = text.rfind("}")
if end < 0 or "exposes retained history separately" in text:
    raise RuntimeError("unexpected phone benchmark test state")
phone_test.write_text(text[:end] + insert + text[end:], encoding="utf-8")

memory_test = Path("observability/in-memory-store/src/test/kotlin/io/github/daniele21/localllm/observability/store/InMemoryTelemetryRepositoryTest.kt")
memory_test.parent.mkdir(parents=True, exist_ok=True)
memory_test.write_text('''package io.github.daniele21.localllm.observability.store

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.BenchmarkKey
import io.github.daniele21.localllm.observability.TelemetryRetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryTelemetryRepositoryTest {
    @Test
    fun `retains immutable benchmark history while replacing active baseline`() {
        val repository = InMemoryTelemetryRepository(
            TelemetryRetentionPolicy(maxBenchmarkBaselines = 2),
        )
        val first = baseline(10L, 5)
        val second = baseline(20L, 6)
        val third = baseline(30L, 7)

        repository.saveBenchmarkBaseline(first)
        repository.saveBenchmarkBaseline(second)
        repository.saveBenchmarkBaseline(third)

        assertEquals(listOf(third), repository.benchmarkBaselines())
        assertEquals(listOf(third, second), repository.benchmarkBaselineHistory())
    }

    private fun baseline(capturedAt: Long, samples: Int) = BenchmarkBaseline(
        key = BenchmarkKey(
            ApplicationId("app"),
            UseCaseId("use-case"),
            ModelDigest("a".repeat(64)),
            ModelLoadKind.WARM,
        ),
        capturedAtEpochMs = capturedAt,
        sampleCount = samples,
        medianTimeToFirstTokenMs = 1.0,
        p95TimeToFirstTokenMs = 2.0,
        medianTotalMs = 3.0,
        p95TotalMs = 4.0,
        medianDecodeTokensPerSecond = 5.0,
    )
}
''', encoding="utf-8")

Path("docs/benchmark-history.md").write_text('''# Retained benchmark history

The telemetry repository exposes two separate views:

- `benchmarkBaselines()` returns one active baseline for each application, use case, model digest and cold/warm load key. Regression checks use only this view.
- `benchmarkBaselineHistory(limit)` returns immutable captures in newest-first order. Replacing the active baseline never rewrites older captures.

Both in-memory and Room stores enforce `TelemetryRetentionPolicy.maxBenchmarkBaselines`. Room schema version 4 adds `benchmark_baseline_history`; migration 3→4 copies every existing active baseline into history without deleting or changing the active row.

The phone-test Benchmarks screen presents active regression results separately from retained captures. Browsing history never changes the active regression anchor.
''', encoding="utf-8")

replace_once(
    "docs/current-state.md",
    """### Block 6 — selective benchmark-history recovery

Status: **NEXT**.

Recover only unique retained-history behavior from PR #33 on a fresh branch from current `main`.""",
    """### Block 6 — selective benchmark-history recovery

Status: **IMPLEMENTED; awaiting pull-request CI and merge**.

Recovered on the current phone-test architecture: immutable retained captures, active-versus-history semantics, bounded in-memory and Room persistence, a non-destructive Room 3→4 migration and historical presentation. The obsolete standalone console was not restored.""",
    "ledger block 6",
)
