package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HarnessDiagnosticsOverviewTest {
    @Test
    fun `empty diagnostics remains explicitly not run and not captured`() {
        val overview = harnessDiagnosticsOverviewState(
            diagnostics = DiagnosticsUiState(null, emptyList(), emptyList()),
            resources = DiagnosticsResourceHistoryUi(),
            benchmarks = BenchmarkUiState(),
            logs = DiagnosticsLogUiState(),
            validationReport = "",
        )

        assertEquals("Not run", overview.health)
        assertEquals(0, overview.runCount)
        assertEquals(0, overview.resourceCount)
        assertEquals(0, overview.benchmarkCount)
        assertEquals(0, overview.logCount)
        assertFalse(overview.validationAvailable)

        val entries = harnessDiagnosticsOverviewEntries(overview)
        assertEquals(6, entries.size)
        assertEquals(HarnessDiagnosticsEvidenceGroup.READINESS, entries.first().group)
        assertEquals("Not run", entries.first { it.section == DiagnosticsSection.RUNS }.status)
        assertEquals("Not captured", entries.first { it.section == DiagnosticsSection.RESOURCES }.status)
        assertEquals("Not captured", entries.first { it.section == DiagnosticsSection.BENCHMARKS }.status)
        assertEquals("Not run", entries.first { it.section == DiagnosticsSection.VALIDATION }.status)
        assertEquals("Empty", entries.first { it.section == DiagnosticsSection.LOGS }.status)
    }

    @Test
    fun `diagnostic evidence is grouped by decision depth`() {
        val entries = harnessDiagnosticsOverviewEntries(
            HarnessDiagnosticsOverviewState(
                health = "Pass",
                runCount = 2,
                resourceCount = 1,
                benchmarkCount = 1,
                logCount = 4,
                validationAvailable = true,
            ),
        )

        assertEquals(
            listOf(DiagnosticsSection.HEALTH),
            entries.filter { it.group == HarnessDiagnosticsEvidenceGroup.READINESS }.map { it.section },
        )
        assertEquals(
            listOf(DiagnosticsSection.RUNS, DiagnosticsSection.RESOURCES, DiagnosticsSection.BENCHMARKS),
            entries.filter { it.group == HarnessDiagnosticsEvidenceGroup.MEASURED_EVIDENCE }.map { it.section },
        )
        assertEquals(
            listOf(DiagnosticsSection.VALIDATION, DiagnosticsSection.LOGS),
            entries.filter { it.group == HarnessDiagnosticsEvidenceGroup.PROOF_AND_TROUBLESHOOTING }.map { it.section },
        )
    }

    @Test
    fun `failed health is presented as an error rather than a warning`() {
        val health = harnessDiagnosticsOverviewEntries(
            HarnessDiagnosticsOverviewState(
                health = "Fail",
                runCount = 0,
                resourceCount = 0,
                benchmarkCount = 0,
                logCount = 0,
                validationAvailable = false,
            ),
        ).first { it.section == DiagnosticsSection.HEALTH }

        assertEquals(HarnessStatusTone.ERROR, health.tone)
    }
}
