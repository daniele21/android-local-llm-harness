package io.github.daniele21.localllm.console.application

import io.github.daniele21.localllm.console.analysis.ValidatedFinding
import io.github.daniele21.localllm.console.document.DocumentDescriptor
import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiDefinitionSet
import io.github.daniele21.localllm.console.redaction.OccurrenceId
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState
import io.github.daniele21.localllm.console.redaction.ReviewOccurrence

internal data class OmbraSensitiveTaskSnapshot(
    val descriptor: DocumentDescriptor?,
    val segments: List<DocumentSegment>,
    val definitions: List<PiiDefinition>,
    val findings: List<ValidatedFinding>,
    val reviewOccurrences: List<ReviewOccurrence>,
) {
    override fun toString(): String = "OmbraSensitiveTaskSnapshot(hasDocument=${descriptor != null}, segmentCount=${segments.size}, " +
        "definitionCount=${definitions.size}, findingCount=${findings.size}, reviewCount=${reviewOccurrences.size})"
}

internal interface OmbraSensitiveTaskStore {
    fun snapshot(): OmbraSensitiveTaskSnapshot

    fun replaceDocument(document: OmbraExtractedDocument)

    fun replaceDefinitions(definitions: Collection<PiiDefinition>)

    fun replaceFindings(findings: Collection<ValidatedFinding>)

    fun updateDecision(occurrenceId: OccurrenceId, decision: ReviewDecisionState): Boolean

    fun clear()
}

internal class InMemoryOmbraSensitiveTaskStore : OmbraSensitiveTaskStore {
    private var descriptor: DocumentDescriptor? = null
    private var segments: List<DocumentSegment> = emptyList()
    private var definitions: List<PiiDefinition> = emptyList()
    private var findings: List<ValidatedFinding> = emptyList()
    private var reviewOccurrences: List<ReviewOccurrence> = emptyList()

    override fun snapshot(): OmbraSensitiveTaskSnapshot = OmbraSensitiveTaskSnapshot(
        descriptor = descriptor,
        segments = segments.toList(),
        definitions = definitions.toList(),
        findings = findings.toList(),
        reviewOccurrences = reviewOccurrences.toList(),
    )

    override fun replaceDocument(document: OmbraExtractedDocument) {
        require(document.segments.all { segment -> segment.pageIndex < document.descriptor.pageCount }) {
            "Document segment page index exceeds document page count"
        }
        descriptor = document.descriptor
        segments = document.segments.toList()
        definitions = emptyList()
        findings = emptyList()
        reviewOccurrences = emptyList()
    }

    override fun replaceDefinitions(definitions: Collection<PiiDefinition>) {
        require(descriptor != null && segments.isNotEmpty()) { "Definitions require an extracted document" }
        val validated = PiiDefinitionSet.create(definitions).getOrThrow()
        this.definitions = validated.definitions
        findings = emptyList()
        reviewOccurrences = emptyList()
    }

    override fun replaceFindings(findings: Collection<ValidatedFinding>) {
        require(descriptor != null && segments.isNotEmpty()) { "Findings require an extracted document" }
        require(definitions.isNotEmpty()) { "Findings require active PII definitions" }
        val allowedTypeIds = definitions.mapTo(linkedSetOf()) { definition -> definition.id }
        require(findings.all { finding -> finding.typeId in allowedTypeIds }) {
            "Finding type must belong to the active definition set"
        }
        val segmentIds = segments.mapTo(linkedSetOf()) { segment -> segment.id }
        require(
            findings.flatMap { finding -> finding.occurrences }.all { occurrence -> occurrence.segmentId in segmentIds },
        ) { "Finding occurrence must belong to the active document" }

        val review =
            findings.flatMap { finding ->
                finding.occurrences.map { occurrence ->
                    ReviewOccurrence(
                        id = OccurrenceId(finding.typeId, occurrence),
                        surface = finding.surface,
                    )
                }
            }
        require(review.map { occurrence -> occurrence.id }.distinct().size == review.size) {
            "Duplicate review occurrence identity"
        }

        this.findings = findings.toList()
        reviewOccurrences = review
    }

    override fun updateDecision(occurrenceId: OccurrenceId, decision: ReviewDecisionState): Boolean {
        val index = reviewOccurrences.indexOfFirst { occurrence -> occurrence.id == occurrenceId }
        if (index < 0) return false
        val current = reviewOccurrences[index]
        val updated = current.copy(decision = decision)
        reviewOccurrences = reviewOccurrences.toMutableList().also { mutable -> mutable[index] = updated }
        return true
    }

    override fun clear() {
        descriptor = null
        segments = emptyList()
        definitions = emptyList()
        findings = emptyList()
        reviewOccurrences = emptyList()
    }
}
