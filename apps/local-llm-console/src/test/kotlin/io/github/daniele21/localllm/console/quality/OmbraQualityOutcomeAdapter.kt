package io.github.daniele21.localllm.console.quality

import io.github.daniele21.localllm.console.analysis.OmbraMergedAnalysis

/**
 * Converts the product's source-validated merged analysis into benchmark occurrences.
 *
 * Raw model output is intentionally not accepted here. The quality scorer therefore
 * measures the same finding-validation boundary that feeds OMBRA review/export.
 */
internal object OmbraQualityOutcomeAdapter {
    fun structured(case: QualityCase, analysis: OmbraMergedAnalysis): QualityCaseOutcome.Structured {
        val allowedSegmentIds = case.segments.mapTo(linkedSetOf(), QualitySegment::id)
        val findings =
            analysis.findings.flatMap { finding ->
                finding.occurrences.map { occurrence ->
                    val segmentId = occurrence.segmentId.value
                    require(segmentId in allowedSegmentIds) {
                        "Validated quality finding references a segment outside its case"
                    }
                    QualityOccurrence(
                        typeId = finding.typeId.value,
                        segmentId = segmentId,
                        startOffset = occurrence.range.startInclusive,
                        endOffset = occurrence.range.endExclusive,
                        surface = finding.surface,
                    )
                }
            }
        return QualityCaseOutcome.Structured(
            caseId = case.id,
            findings = findings,
            invalidFindingCount = analysis.invalidFindingCount,
        )
    }

    fun invalidResult(case: QualityCase): QualityCaseOutcome.InvalidResult = QualityCaseOutcome.InvalidResult(caseId = case.id)

    fun incomplete(case: QualityCase): QualityCaseOutcome.Incomplete = QualityCaseOutcome.Incomplete(caseId = case.id)
}
